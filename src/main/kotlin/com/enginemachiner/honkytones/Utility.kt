package com.enginemachiner.honkytones

import org.apache.commons.validator.routines.UrlValidator

fun isValidUrl(url: String): Boolean {

    val b = !url.startsWith("http://") && !url.startsWith("https://")

    var url = url;          if (b) url = "http://$url"

    return UrlValidator().isValid(url)

}