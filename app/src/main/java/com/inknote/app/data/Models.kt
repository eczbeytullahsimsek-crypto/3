package com.inknote.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class NotebookKind { NOTEBOOK, PDF }
enum class Scheme { PRIMARY, TERTIARY, SECONDARY, SURFACE, PDF }
enum class Folder(val label: String) {
    UNI("Üniversite & Dersler"), UX("UX/UI Tasarım"), DIARY("Kişisel Günlük"), PDFS("İçe Aktarılan PDF'ler")
}

enum class PageTemplate(val label: String, val sub: String) {
    BLANK("Boş Kağıt", "Serbest çizim"),
    DOT("Noktalı Grid", "5mm Izgara"),
    RULED("Çizgili (Dar)", "6.5mm aralık"),
    GRID("Kareli", "5mm Kareli"),
    CORNELL("Cornell Metodu", "Özet & ipuçları"),
    WEEKLY("Haftalık Ajanda", "Pazartesi-Pazar"),
    STAFF("Porte / Müzik", "5 çizgili nota");
}

/** Kağıt tonları: ad, arka plan (ARGB), koyu mu */
val PaperTones = listOf(
    Triple("Fildişi", 0xFFFDFBF7.toInt(), false),
    Triple("Eskiz", 0xFFFBF3D5.toInt(), false),
    Triple("Gece", 0xFF202024.toInt(), true),
    Triple("Mavi", 0xFFE8EEF5.toInt(), false),
    Triple("Kraft", 0xFFDCC9B3.toInt(), false),
)

/** Kağıt oranları (genişlik / yükseklik) */
enum class PaperSize(val label: String, val ratio: Float) {
    A4("A4", 1f / 1.4142f), A5("A5", 1f / 1.4142f), LETTER("Letter", 8.5f / 11f), INFINITE("Sonsuz", 1f / 3f)
}

data class Notebook(
    val id: String,
    val title: String,
    val tag: String,
    val desc: String,
    val kind: NotebookKind,
    val scheme: Scheme,
    val folder: Folder,
    val pageCount: Int,
    val starred: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastPage: Int = 0,
    val defaultTemplate: PageTemplate = PageTemplate.DOT,
    val templates: Map<Int, PageTemplate> = emptyMap(),
    val paperTone: Int = 0,
    val paperSize: PaperSize = PaperSize.A4,
    val trashed: Boolean = false,
) {
    fun templateOf(page: Int) = templates[page] ?: defaultTemplate

    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("tag", tag); put("desc", desc)
        put("kind", kind.name); put("scheme", scheme.name); put("folder", folder.name)
        put("pageCount", pageCount); put("starred", starred); put("updatedAt", updatedAt)
        put("lastPage", lastPage); put("defaultTemplate", defaultTemplate.name)
        put("paperTone", paperTone); put("paperSize", paperSize.name); put("trashed", trashed)
        put("templates", JSONObject().also { o -> templates.forEach { (k, v) -> o.put(k.toString(), v.name) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Notebook {
            val tpl = HashMap<Int, PageTemplate>()
            o.optJSONObject("templates")?.let { t ->
                t.keys().forEach { k ->
                    runCatching { tpl[k.toInt()] = PageTemplate.valueOf(t.getString(k)) }
                }
            }
            return Notebook(
                id = o.getString("id"), title = o.getString("title"),
                tag = o.optString("tag"), desc = o.optString("desc"),
                kind = NotebookKind.valueOf(o.getString("kind")),
                scheme = Scheme.valueOf(o.getString("scheme")),
                folder = Folder.valueOf(o.getString("folder")),
                pageCount = o.getInt("pageCount"), starred = o.optBoolean("starred"),
                updatedAt = o.optLong("updatedAt"), lastPage = o.optInt("lastPage"),
                defaultTemplate = runCatching { PageTemplate.valueOf(o.getString("defaultTemplate")) }.getOrDefault(PageTemplate.DOT),
                templates = tpl, paperTone = o.optInt("paperTone"),
                paperSize = runCatching { PaperSize.valueOf(o.getString("paperSize")) }.getOrDefault(PaperSize.A4),
                trashed = o.optBoolean("trashed"),
            )
        }
    }
}

/** Sayfa genişliğine göre normalize edilmiş tek bir ink noktası. */
class Pt(val x: Float, val y: Float, val t: Long, val p: Float)

/**
 * Kalıcı hâle getirilmiş bir çizgi. Koordinatlar ve kalınlık sayfa GENİŞLİĞİNE bölünmüştür (normalize),
 * böylece farklı ekran boyutlarında aynı görünür.
 */
class StrokeData(
    val kind: String,      // pen | ball | brush | hl
    val argb: Int,
    val size: Float,
    val tool: Int,         // 0 stylus, 1 touch, 2 mouse
    val pts: List<Pt>,
    val timeOffsetMs: Long = 0L, // Ses kaydının başlangıcına göre zaman (ms)
)

data class AudioRecording(
    val id: String,
    val notebookId: String,
    val title: String,
    val filePath: String,
    val createdAt: Long,
    val durationMs: Long,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("notebookId", notebookId)
        put("title", title)
        put("filePath", filePath)
        put("createdAt", createdAt)
        put("durationMs", durationMs)
    }

    companion object {
        fun fromJson(o: org.json.JSONObject): AudioRecording = AudioRecording(
            id = o.getString("id"),
            notebookId = o.getString("notebookId"),
            title = o.optString("title", "Ses Kaydı"),
            filePath = o.getString("filePath"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            durationMs = o.optLong("durationMs", 0L),
        )
    }
}

object StrokeCodec {
    fun encode(list: List<StrokeData>): String {
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
            o.put("k", s.kind); o.put("c", s.argb); o.put("s", s.size.toDouble()); o.put("tool", s.tool)
            if (s.timeOffsetMs > 0L) o.put("tm", s.timeOffsetMs)
            val p = JSONArray()
            s.pts.forEach { pt ->
                p.put(pt.x.toDouble()); p.put(pt.y.toDouble()); p.put(pt.t); p.put(pt.p.toDouble())
            }
            o.put("p", p)
            arr.put(o)
        }
        return arr.toString()
    }

    fun decode(text: String): List<StrokeData> {
        val out = ArrayList<StrokeData>()
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tm = o.optLong("tm", 0L)
            val p = o.getJSONArray("p")
            val pts = ArrayList<Pt>(p.length() / 4)
            var j = 0
            while (j + 3 < p.length()) {
                pts.add(Pt(p.getDouble(j).toFloat(), p.getDouble(j + 1).toFloat(), p.getLong(j + 2), p.getDouble(j + 3).toFloat()))
                j += 4
            }
            if (pts.isNotEmpty()) out.add(StrokeData(o.getString("k"), o.getInt("c"), o.getDouble("s").toFloat(), o.optInt("tool"), pts, tm))
        }
        return out
    }
}
