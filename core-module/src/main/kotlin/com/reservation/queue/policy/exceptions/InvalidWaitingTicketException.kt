package com.reservation.queue.policy.exceptions

import com.reservation.exceptions.ClientException

class InvalidWaitingTicketException(
    message: String = "Waiting ticket is invalid.",
) : ClientException(message)
