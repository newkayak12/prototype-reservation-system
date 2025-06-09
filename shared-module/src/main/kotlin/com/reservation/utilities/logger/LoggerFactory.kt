package com.reservation.utilities.logger

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> loggerFactory(): Logger = LoggerFactory.getLogger(T::class.java.simpleName)
