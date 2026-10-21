package com.vknxworks.printctrl.data.net

import androidx.annotation.StringRes
import java.io.IOException

/**
 * A failure whose explanation for people lives in strings.xml: friendlyMessage shows [text] filled in with [args].
 * The exception's own message only names the resource, for logs.
 */
class UserMessageException(@StringRes val text: Int, vararg val args: Any) : IOException("UserMessage($text)")
