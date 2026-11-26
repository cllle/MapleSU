package com.maplesu.core.ui.screen.flash

import android.net.Uri

sealed interface FlashIt { data class FlashModules(val uris: List<Uri>) : FlashIt }
