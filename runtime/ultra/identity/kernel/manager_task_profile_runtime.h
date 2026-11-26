/* SPDX-License-Identifier: GPL-3.0-only */
#ifndef MAPLESU_MANAGER_TASK_PROFILE_RUNTIME_H
#define MAPLESU_MANAGER_TASK_PROFILE_RUNTIME_H
#include <linux/types.h>

/* Boot-only, after the dynamic layout and native symbol/CFI resolver are live.
 * Must run under the Profile trace mutex, before registering any callback. */
int msu_task_profile_runtime_produce(unsigned long (*lookup)(const char *));
bool msu_task_profile_runtime_ready(void);
#endif
