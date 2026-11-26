package com.maplesu.core.ui.webui
import android.webkit.WebView
class WebUIState { var webView:WebView?=null;var modDir="";var moduleId:String?=null;var moduleInfoJson:String?=null;var isModuleSessionCurrent=true;var uiEvent:WebUIEvent?=null;var isInsetsEnabled=false;fun requestExit(){uiEvent=WebUIEvent.Close};fun acceptsPage(view:WebView)=view===webView }
sealed interface WebUIEvent {data object Close:WebUIEvent}
fun webUIApps()=emptyList<com.maplesu.core.data.model.AppInfo>()
