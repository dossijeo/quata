package com.quata.documentreader

import com.quata.documentreader.dataType.LangDataType

interface OnItemClickSelectLang {

    fun onItemClick(langDataType: LangDataType?, i: Int)
}