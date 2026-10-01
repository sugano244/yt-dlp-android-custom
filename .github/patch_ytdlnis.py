from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Patch target not found: {label}")
    return text.replace(old, new, 1)

# 1) Avoid YouTube HLS 233/234 for section audio downloads.
p = Path("app/src/main/java/com/deniscerri/ytdl/util/extractors/ytdlp/YTDLPUtil.kt")
s = p.read_text()
old = '''                if (audioQualityId.isNotBlank()) {
                    if (audioQualityId.matches(".*-[0-9]+.*".toRegex())) {'''
new = '''                // Avoid YouTube HLS audio 233/234 when section cutting.
                if (downloadItem.downloadSections.isNotBlank() &&
                    downloadItem.url.isYoutubeURL() &&
                    (downloadItem.format.format_id == "233" ||
                     downloadItem.format.format_id == "234" ||
                     audioQualityId.startsWith("233/") ||
                     audioQualityId.startsWith("234/"))) {
                    audioQualityId = "140/ba[ext=m4a]/ba/b"
                }

                if (audioQualityId.isNotBlank()) {
                    if (audioQualityId.matches(".*-[0-9]+.*".toRegex())) {'''
s = replace_once(s, old, new, "YouTube section format")
p.write_text(s)

# 2) Accurate numeric progress for section downloads.
p = Path("app/src/main/java/com/deniscerri/ytdl/ui/downloads/ActiveDownloadsFragment.kt")
s = p.read_text()
old = '''                            progressBar?.setProgressCompat(event.progress, true)
                            outputText?.text = event.output'''
new = '''                            val currentItem = activeDownloads.currentList
                                .filterNotNull()
                                .firstOrNull { it.id == event.downloadItemID }
                            val displayProgress = calculateDisplayedProgress(
                                currentItem,
                                event.progress,
                                progressBar?.progress ?: 0,
                                event.output
                            )
                            progressBar?.setProgressCompat(displayProgress, true)
                            outputText?.text = displayProgress.toString() + "%  " + event.output'''
s = replace_once(s, old, new, "active download progress update")

insert_before = "\n    override fun onCancelClick(itemID: Long) {"
helper = r'''
    private fun parseProgressTimeSeconds(value: String): Double? {
        val parts = value.trim().split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toDouble() * 3600.0 + parts[1].toDouble() * 60.0 + parts[2].toDouble()
                2 -> parts[0].toDouble() * 60.0 + parts[1].toDouble()
                1 -> parts[0].toDouble()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getFirstSectionDurationSeconds(spec: String): Double? {
        val first = spec.trim().removePrefix("*").split(";").firstOrNull()?.trim() ?: return null
        val clean = first.substringBefore(" [").trim()
        val separator = clean.indexOf('-')
        if (separator <= 0 || separator >= clean.length - 1) return null
        val start = parseProgressTimeSeconds(clean.substring(0, separator)) ?: return null
        val end = parseProgressTimeSeconds(clean.substring(separator + 1)) ?: return null
        return (end - start).takeIf { it > 0.0 }
    }

    private fun calculateDisplayedProgress(
        item: DownloadItem?,
        fallback: Int,
        current: Int,
        output: String
    ): Int {
        if (item == null || item.downloadSections.isBlank()) {
            return fallback.coerceIn(0, 100)
        }

        val timeMatch = Regex("time=\\s*(\\d+:\\d{2}:\\d{2}(?:\\.\\d+)?)").find(output)
        val elapsed = timeMatch?.groupValues?.getOrNull(1)?.let { parseProgressTimeSeconds(it) }
        val total = getFirstSectionDurationSeconds(item.downloadSections)
        if (elapsed != null && total != null && total > 0.0) {
            return ((elapsed / total) * 100.0).toInt().coerceIn(0, 99)
        }

        if (fallback >= 100 && (
                output.contains("Moving file", ignoreCase = true) ||
                output.contains("Moved file", ignoreCase = true) ||
                output.contains("Scanning Files", ignoreCase = true)
            )) {
            return 100
        }

        return current.coerceIn(0, 99)
    }

'''
s = replace_once(s, insert_before, helper + insert_before, "progress helper insertion")
p.write_text(s)

# 3) Long-video cutting: keep slider values in seconds, timestamps in milliseconds.
p = Path("app/src/main/java/com/deniscerri/ytdl/ui/downloadcard/CutVideoBottomSheetDialog.kt")
s = p.read_text()

s = replace_once(
    s,
    "    private var endTimestamp = 0L\n",
    "    private var endTimestamp = 0L\n    private var previewingCutEnd = false\n    private var holdAtCutEnd = false\n    private val endPreviewDurationMs = 5000L\n",
    "cut preview state"
)

s = replace_once(
    s,
    "        rangeSlider.valueTo = itemDurationTimestamp.toFloat()",
    "        rangeSlider.valueTo = (itemDurationTimestamp / 1000f).coerceAtLeast(0.001f)",
    "slider duration units"
)
s = replace_once(
    s,
    "        if (updateSlider) rangeSlider.setValues(millis.toFloat(), endTimestamp.toFloat())",
    "        if (updateSlider) rangeSlider.setValues(millis / 1000f, endTimestamp / 1000f)",
    "start slider units"
)
s = replace_once(
    s,
    "        if (updateSlider) rangeSlider.setValues(startTimestamp.toFloat(), millis.toFloat())",
    "        if (updateSlider) rangeSlider.setValues(startTimestamp / 1000f, millis / 1000f)",
    "end slider units"
)
s = replace_once(
    s,
    "            setStartTimestamp(rangeSlider.values[0].toLong(), updateSlider = false)",
    "            setStartTimestamp((rangeSlider.values[0] * 1000f).toLong(), updateSlider = false)",
    "slider start conversion"
)
s = replace_once(
    s,
    "            setEndTimestamp(rangeSlider.values[1].toLong(), updateSlider = false)",
    "            setEndTimestamp((rangeSlider.values[1] * 1000f).toLong(), updateSlider = false)",
    "slider end conversion"
)

old = '''        forwardBtn.setOnClickListener {
            kotlin.runCatching {
                player.seekTo(max(startTimestamp, endTimestamp - 1500))
                player.play()
            }
        }'''
new = '''        forwardBtn.setOnClickListener {
            kotlin.runCatching {
                holdAtCutEnd = false
                previewingCutEnd = true
                player.seekTo(max(startTimestamp, endTimestamp - endPreviewDurationMs))
                player.play()
            }
        }'''
s = replace_once(s, old, new, "end preview button")

old = '''                if (endTextInput.text.isNotBlank()) {
                    if (currentTime >= endTimestamp || (!player.isPlaying && currentTime >= endTimestamp - pollProgressInterval)) {
                        player.prepare()
                        player.seekTo(startTimestamp)
                    }
                }'''
new = '''                if (endTextInput.text.isNotBlank() && !holdAtCutEnd) {
                    if (currentTime >= endTimestamp) {
                        if (previewingCutEnd) {
                            previewingCutEnd = false
                            holdAtCutEnd = true
                            player.pause()
                            player.seekTo(endTimestamp)
                        } else {
                            player.seekTo(startTimestamp)
                        }
                    }
                }'''
s = replace_once(s, old, new, "end preview playback handling")

old = '''        rewindBtn.setOnClickListener {
            try {
                val stmp = startTextInput.text.toString().convertToTimestamp()
                player.seekTo(stmp)
                player.play()
            }catch (ignored: Exception) {}
        }'''
new = '''        rewindBtn.setOnClickListener {
            try {
                previewingCutEnd = false
                holdAtCutEnd = false
                val stmp = startTextInput.text.toString().convertToTimestamp()
                player.seekTo(stmp)
                player.play()
            }catch (ignored: Exception) {}
        }'''
s = replace_once(s, old, new, "start preview button")

old = '''        setStartTimestamp(timestamp, updateTextInput = true) // also update text input to normalize
        player.seekTo(timestamp)
        player.play()'''
new = '''        previewingCutEnd = false
        holdAtCutEnd = false
        setStartTimestamp(timestamp, updateTextInput = true) // also update text input to normalize
        player.seekTo(timestamp)
        player.play()'''
s = replace_once(s, old, new, "manual start preview")

old = '''        setEndTimestamp(timestamp, updateTextInput = true) // also update text input to normalize
        player.seekTo(timestamp - 1500)
        player.play()'''
new = '''        holdAtCutEnd = false
        previewingCutEnd = true
        setEndTimestamp(timestamp, updateTextInput = true) // also update text input to normalize
        player.seekTo(max(startTimestamp, timestamp - endPreviewDurationMs))
        player.play()'''
s = replace_once(s, old, new, "manual end preview")

old = '''    private fun updateFromSlider() {
        val draggedFromBeginning = rangeSlider.focusedThumbIndex != 1'''
new = '''    private fun updateFromSlider() {
        holdAtCutEnd = false
        previewingCutEnd = rangeSlider.focusedThumbIndex == 1
        val draggedFromBeginning = rangeSlider.focusedThumbIndex != 1'''
s = replace_once(s, old, new, "slider preview state")
s = replace_once(
    s,
    "                player.seekTo(max(startTimestamp, endTimestamp - 1500))",
    "                player.seekTo(max(startTimestamp, endTimestamp - endPreviewDurationMs))",
    "slider end preview duration"
)

p.write_text(s)

# Wider timestamp inputs make hour-based timestamps clearly editable.
p = Path("app/src/main/res/layout/cut_video_sheet.xml")
s = p.read_text()
if s.count('android:minWidth="70dp"') < 2:
    raise SystemExit("Patch target not found: timestamp input widths")
s = s.replace('android:minWidth="70dp"', 'android:minWidth="110dp"', 2)
p.write_text(s)

# 4) Dedicated package + visible patched app name.
p = Path("app/build.gradle")
s = p.read_text()
s = replace_once(s, 'applicationId "com.deniscerri.ytdl"', 'applicationId "com.deniscerri.ytdl.patched"', "application id")
s = replace_once(s, "def versionBuild = 0", 'def versionBuild = Integer.parseInt(System.getenv("GITHUB_RUN_NUMBER") ?: "1")', "version build")
p.write_text(s)

p = Path("app/src/main/AndroidManifest.xml")
s = p.read_text()
s = replace_once(s, 'android:label="YTDLnis"', 'android:label="YTDLnis Patched"', "app label")
p.write_text(s)
