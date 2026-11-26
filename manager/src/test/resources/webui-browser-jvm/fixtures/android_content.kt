package android.content.pm
class ApplicationInfo { var flags=0; var uid=0; var nonLocalizedLabel:CharSequence?=null; companion object {const val FLAG_SYSTEM=1} }
class PackageInfo { var applicationInfo:ApplicationInfo?=null; var packageName="";var versionName:String?="" }
