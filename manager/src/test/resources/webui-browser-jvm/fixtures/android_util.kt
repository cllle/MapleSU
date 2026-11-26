package android.util
object Base64 {
 const val DEFAULT=0;const val NO_WRAP=2
 @JvmStatic fun encodeToString(bytes:ByteArray,offset:Int,size:Int,flags:Int)=java.util.Base64.getEncoder().encodeToString(bytes.copyOfRange(offset,offset+size))
 @JvmStatic fun decode(value:String,flags:Int)=java.util.Base64.getDecoder().decode(value)
}
object Log {@JvmStatic fun w(tag:String,message:String)=0;@JvmStatic fun w(tag:String,message:String,error:Throwable)=0;@JvmStatic fun e(tag:String,message:String,error:Throwable)=0}
