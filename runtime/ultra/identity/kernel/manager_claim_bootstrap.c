// SPDX-License-Identifier: GPL-3.0-only
#include "manager_claim_bootstrap.h"
#include "../../authorization/request_protocol.h"

/* The manifest collector reads these actual compiled sizes, not Python constants. */
const msu_loader_u32 msu_manager_deployment_support_facts[] = {
    MSU_CLAIM_DEPLOYMENT_ANCHOR_VERSION,
    sizeof(struct msu_resident_deployment_binding),
    sizeof(struct msu_resident_config_v2),
    sizeof(struct msu_deployment_namespace_record),
};

typedef int (*msu_permissions)(unsigned long, int);
typedef int (*msu_materializer)(const struct msu_loader_binding *, const void *, void *, msu_loader_u32);

static int msu_resident_start(const struct msu_manager_claim_anchor *a,
    unsigned int initialize, struct msu_resident_slot **published)
{
    const struct msu_loader_header *h;
    const unsigned char *image;
    unsigned char *memory;
    msu_permissions nx, rw;
    struct msu_resident_slot *slot;
    const struct msu_resident_binding *resident;
    const struct msu_deployment_namespace_record *deployment = 0;
    unsigned long page_mask, bytes;
    unsigned int i;
    int entry, pages, initialized = 0, error = 0;
    msu_loader_u32 expected;

    if (!a || a->magic != MSU_CLAIM_ANCHOR_MAGIC ||
        (a->version != MSU_CLAIM_RESIDENT_ANCHOR_VERSION &&
         a->version != MSU_CLAIM_BOOT_ANCHOR_VERSION &&
         a->version != MSU_CLAIM_DEPLOYMENT_ANCHOR_VERSION) ||
        (a->page_shift != 12 && a->page_shift != 14 && a->page_shift != 16) ||
        a->allocator_kind > 1 || a->binding.image_bytes < sizeof(*a) ||
        a->binding.descriptor_bytes < sizeof(*h) ||
        a->binding.descriptor_offset % 4 || a->self_offset % 4 ||
        a->binding.descriptor_offset > a->binding.image_bytes ||
        a->binding.descriptor_bytes > a->binding.image_bytes - a->binding.descriptor_offset ||
        a->self_offset > a->binding.image_bytes - sizeof(*a) ||
        a->credential_offset > a->binding.image_bytes - MSU_MANAGER_CLAIM_KEY_BYTES)
        return -22;
    if (!a->reserved[1] || a->reserved[1] % 4 ||
        a->reserved[1] > a->binding.image_bytes - 4 ||
        a->reserved[2] || a->reserved[3]) return -22;
    {
        const msu_loader_u32 offsets[] = {
            a->materializer_offset, a->allocate_offset, a->release_offset,
            a->set_nx_offset, a->set_rw_offset, a->set_ro_offset, a->set_x_offset,
            a->cache_offset,
        };
        for (i = 0; i < sizeof(offsets) / sizeof(offsets[0]); ++i)
            if (offsets[i] % 4 || offsets[i] > a->binding.image_bytes - 4) return -22;
    }
    image = (const unsigned char *)a - a->self_offset;
    h = (const void *)(image + a->binding.descriptor_offset);
    if (!h->memory_bytes || h->memory_bytes > MSU_LOADER_MAX_MEMORY_BYTES) return -22;
    {
        if (a->reserved[0] % 4 || a->reserved[0] > a->binding.image_bytes - sizeof(*resident)) return -22;
        resident = (const void *)(image + a->reserved[0]);
        if (a->version == MSU_CLAIM_DEPLOYMENT_ANCHOR_VERSION) {
            const struct msu_resident_deployment_binding *binding;
            if (a->binding.image_bytes < sizeof(*binding) ||
                a->reserved[0] > a->binding.image_bytes - sizeof(*binding)) return -22;
            binding = (const void *)resident;
            if (binding->version != MSU_RESIDENT_DEPLOYMENT_BINDING_VERSION ||
                binding->bytes != sizeof(*binding) || binding->reserved ||
                binding->namespace_bytes != sizeof(*deployment) || binding->namespace_offset % 4 ||
                binding->namespace_offset > a->binding.image_bytes ||
                binding->namespace_bytes > a->binding.image_bytes - binding->namespace_offset) return -22;
            deployment = (const void *)(image + binding->namespace_offset);
            if (!msu_deployment_namespace_valid(deployment) ||
                binding->crc32 != msu_resident_deployment_crc(binding, deployment)) return -22;
            resident = &binding->resident;
        }
        if (resident->reserved ||
            resident->slot_offset % 64 || resident->image_memory_bytes < sizeof(*slot) ||
            resident->slot_offset > resident->image_memory_bytes - sizeof(*slot) ||
            resident->append_offset > a->binding.image_bytes ||
            resident->append_bytes > a->binding.image_bytes - resident->append_offset ||
            (resident->append_bytes != MAPLESU_LEGACY_METADATA_V2_BYTES &&
             resident->append_bytes != MAPLESU_LEGACY_METADATA_AUDIT_BYTES &&
             resident->append_bytes != MAPLESU_APPEND64_BYTES) ||
            resident->init_offset % 4 || resident->discard_offset % 4 || resident->dispatch_offset % 4 ||
            h->memory_bytes < 4 || resident->init_offset > h->memory_bytes - 4 ||
            resident->discard_offset > h->memory_bytes - 4 || resident->dispatch_offset > h->memory_bytes - 4)
            return -22;
        slot = (void *)(image + resident->slot_offset);
        if (__atomic_load_n(&slot->boot_magic, __ATOMIC_ACQUIRE) != MSU_RESIDENT_BOOT_MAGIC) return -11;
        *published = slot;
        if (__atomic_load_n(&slot->state, __ATOMIC_ACQUIRE) == MSU_RESIDENT_READY) {
            return 0;
        }
        if (!initialize) return -11;
        expected = MSU_RESIDENT_EMPTY;
        if (!__atomic_compare_exchange_n(&slot->state, &expected, MSU_RESIDENT_INITIALIZING,
                0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE))
            return expected == MSU_RESIDENT_READY ? 0 : -16;
        slot->last_error = -22;
    }
    page_mask = (1UL << a->page_shift) - 1;
    bytes = (h->memory_bytes + page_mask) & ~page_mask;
    pages = bytes >> a->page_shift;
    nx = (msu_permissions)(image + a->set_nx_offset);
    rw = (msu_permissions)(image + a->set_rw_offset);
    if (a->allocator_kind)
        memory = ((void *(*)(unsigned int, unsigned long))(image + a->allocate_offset))(0, bytes);
    else
        memory = ((void *(*)(unsigned long))(image + a->allocate_offset))(bytes);
    if (!memory) {
        slot->last_error = -12;
        __atomic_store_n(&slot->state, MSU_RESIDENT_EMPTY, __ATOMIC_RELEASE);
        return -12;
    }
    error = nx((unsigned long)memory, pages);
    if (error) goto cleanup;
    error = rw((unsigned long)memory, pages);
    if (error) goto cleanup;
    entry = ((msu_materializer)(image + a->materializer_offset))(&a->binding, image, memory, bytes);
    if (entry <= 0) {
        slot->last_error = entry ? entry : -22;
        goto cleanup;
    }
    error = ((int (*)(const struct msu_manager_claim_anchor *,
        const struct msu_loader_header *, void *, int))(image + a->reserved[1]))(a, h, memory, pages);
    if (error) goto cleanup;
    {
        struct msu_resident_config config;
        struct msu_resident_config_v2 config_v2;
        const struct msu_resident_config *selected = &config;
        config.version = 1;
        config.append_bytes = resident->append_bytes;
        config.image = image;
        config.append = image + resident->append_offset;
        config.credential = (const char *)image + a->credential_offset;
        if (deployment) {
            config_v2.version = 2;
            config_v2.config_bytes = sizeof(config_v2);
            config_v2.append_bytes = config.append_bytes;
            config_v2.namespace_record_bytes = sizeof(*deployment);
            config_v2.image = config.image;
            config_v2.append = config.append;
            config_v2.credential = config.credential;
            config_v2.namespace_record = deployment;
            selected = (const void *)&config_v2;
        }
        initialized = 1;
        entry = ((int (*)(const struct msu_resident_config *))(memory + resident->init_offset))(selected);
        if (entry) { slot->last_error = entry; goto cleanup; }
        slot->memory = (msu_loader_uptr)memory;
        slot->dispatch = (msu_loader_uptr)(memory + resident->dispatch_offset);
        slot->allocation_bytes = bytes;
        slot->last_error = 0;
        __atomic_store_n(&slot->state, MSU_RESIDENT_READY, __ATOMIC_RELEASE);
        return 0;
    }
cleanup:
    if (error) slot->last_error = error;
    error = slot->last_error;
    if (initialized &&
        ((int (*)(void))(memory + resident->discard_offset))()) {
        slot->memory = (msu_loader_uptr)memory;
        slot->allocation_bytes = bytes;
        __atomic_store_n(&slot->state, MSU_RESIDENT_QUARANTINED, __ATOMIC_RELEASE);
        return error;
    }
    /* Retain an allocation on a failed permission rollback. Returning it to
     * the allocator with uncertain page attributes would corrupt later users. */
    if (!nx((unsigned long)memory, pages) && !rw((unsigned long)memory, pages)) {
        ((void (*)(void *))(image + a->release_offset))(memory);
        __atomic_store_n(&slot->state, MSU_RESIDENT_EMPTY, __ATOMIC_RELEASE);
    } else {
        slot->memory = (msu_loader_uptr)memory;
        slot->allocation_bytes = bytes;
        __atomic_store_n(&slot->state, MSU_RESIDENT_QUARANTINED, __ATOMIC_RELEASE);
    }
    return error;
}

int msu_manager_resident_boot(const struct msu_manager_claim_anchor *a)
{
    struct msu_resident_slot *slot;
    if (!a || a->version != MSU_CLAIM_BOOT_ANCHOR_VERSION) return -22;
    return msu_resident_start(a, 1, &slot);
}

int msu_manager_resident_boot_deployment(const struct msu_manager_claim_anchor *a)
{
    struct msu_resident_slot *slot;
    if (!a || a->version != MSU_CLAIM_DEPLOYMENT_ANCHOR_VERSION) return -22;
    return msu_resident_start(a, 1, &slot);
}

void msu_manager_claim_bootstrap(const struct msu_manager_claim_anchor *a, const char *name)
{
    struct msu_resident_slot *slot;
    unsigned int i, length = 0;
    if (!name) return;
    while (length <= 85 && name[length]) ++length;
    if (!msu_manager_claim_address(name, length)) {
        if (length != 84) return;
        for (i = 0; i < MAPLESU_OWNER_ENVELOPE_PREFIX_BYTES; ++i)
            if (name[i] != MAPLESU_OWNER_ENVELOPE_PREFIX[i]) return;
    }
    if (!a || msu_resident_start(a, a->version == MSU_CLAIM_RESIDENT_ANCHOR_VERSION, &slot)) return;
    ((int (*)(const char *))(msu_loader_uptr)slot->dispatch)(name);
}
