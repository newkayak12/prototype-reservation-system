package com.reservation.queue.exceptions

import com.reservation.exceptions.ClientException

class QueueNotAdmittedException(
    message: String = "This ticket has not been admitted from the waiting queue yet.",
) : ClientException(message)
