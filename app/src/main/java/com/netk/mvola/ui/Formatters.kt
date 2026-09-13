package com.netk.mvola.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.asCurrency(): String = "${NumberFormat.getIntegerInstance(Locale.FRANCE).format(this)} Ar"
fun Long.asDate(): String = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.FRANCE).format(Date(this))
