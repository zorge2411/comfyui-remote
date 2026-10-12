package com.example.comfyui_remote.domain

import java.net.URLEncoder

/** ComfyUI `/view` URLs (Phase 104): the one place they're built, with the query values encoded. */
object MediaUrls {

    /** [base] is "http(s)://host:port" without a trailing slash. */
    fun view(base: String, filename: String, subfolder: String? = null, type: String = "output"): String {
        val query = buildList {
            add("filename=" + encode(filename))
            if (!subfolder.isNullOrEmpty()) add("subfolder=" + encode(subfolder))
            add("type=" + encode(type))
        }
        return "$base/view?" + query.joinToString("&")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
