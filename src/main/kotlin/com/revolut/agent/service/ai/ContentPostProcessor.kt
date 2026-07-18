package com.revolut.agent.service.ai

import com.revolut.agent.model.settings.EmojiPolicy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ContentPostProcessor {

	private val log = LoggerFactory.getLogger(javaClass)

	fun process(raw: String, brief: ContentBrief): String {
		var text = raw.trim()
		if (text.isEmpty()) {
			throw IllegalStateException("Generated content is empty")
		}

		if (brief.emojiPolicyName == EmojiPolicy.NONE.name) {
			text = stripEmojis(text).trim()
		}

		val len = text.length
		if (len < brief.targetLength.min || len > brief.targetLength.max) {
			log.warn(
				"Generated content length {} outside target {}-{} (format={}, lang={})",
				len,
				brief.targetLength.min,
				brief.targetLength.max,
				brief.formatChosen,
				brief.language,
			)
		}

		if ('#' !in text && brief.hashtags.isNotEmpty()) {
			text = (text.trimEnd() + "\n\n" + brief.hashtags.joinToString(" ")).trim()
		}

		return text.trim()
	}

	private fun stripEmojis(input: String): String {
		val out = StringBuilder(input.length)
		var i = 0
		while (i < input.length) {
			val cp = input.codePointAt(i)
			if (!isEmojiCodePoint(cp)) {
				out.appendCodePoint(cp)
			}
			i += Character.charCount(cp)
		}
		return out.toString().replace(Regex("[ \\t]{2,}"), " ")
	}

	private fun isEmojiCodePoint(cp: Int): Boolean =
		cp in 0x1F300..0x1FAFF ||
			cp in 0x2600..0x27BF ||
			cp == 0x200D ||
			cp == 0xFE0F ||
			cp in 0x1F1E6..0x1F1FF
}
