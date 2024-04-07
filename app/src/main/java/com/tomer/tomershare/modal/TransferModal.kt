package com.tomer.tomershare.modal

//Modal class for trans rv items
data class TransferModal(
    var fileName: String,
    var isTrans: Boolean = false
) {
    init {
        if (fileName.startsWith("...fol")) {

            val allFol = fileName.substring(7).split("/")
            fileName = if (allFol.size==2) {
                val folNameIndex = fileName.indexOf('/', 7, true)
                "\uD83D\uDCC2 ${fileName.subSequence(7, folNameIndex)} → ${fileName.substring(folNameIndex + 1)}"
            }else{
                val sb = StringBuilder()
                for (i in 0..allFol.size-2){
                    sb.append("\uD83D\uDCC2 ")
                    sb.append(allFol[i])
                    sb.append(" ")
                }
                sb.append("→ ")
                sb.append(allFol[allFol.size-1])
                sb.toString()
            }
        }
    }
}
