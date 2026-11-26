package com.maplesu.core.data.model
import android.content.pm.PackageInfo
class AppInfo(val label:String,val packageInfo:PackageInfo,val profile:Any?=null,val special:Boolean=false) {
 val packageName get()=packageInfo.packageName
 val uid get()=packageInfo.applicationInfo?.uid?:-1
}
