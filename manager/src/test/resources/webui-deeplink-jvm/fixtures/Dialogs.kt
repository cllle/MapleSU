package com.maplesu.core.ui.component.dialog

class Dialog { fun showConfirm(title: String, content: String) {} }
fun rememberConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit): Dialog = Dialog()
