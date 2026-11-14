package app.mimusica

import app.mimusica.domain.importer.ImportedRow
import app.mimusica.domain.importer.PlaylistTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTextParserTest {

    @Test
    fun parsesExportifyCsv() {
        val result = PlaylistTextParser.parse(Fixtures.read("exportify.csv"), "content://x/Mi_Lista.csv")
        assertEquals("Mi Lista", result.suggestedName)
        assertEquals(3, result.rows.size)
        assertEquals(
            ImportedRow("Bohemian Rhapsody - Remastered 2011", "Queen", "A Night At The Opera (2011 Remaster)"),
            result.rows[0]
        )
        assertEquals("Luis Fonsi,Daddy Yankee", result.rows[1].artist)
        assertEquals("Canción con \"comillas\", y coma", result.rows[2].title)
    }

    @Test
    fun parsesSemicolonCsvWithBomAndCrlf() {
        val csv = "﻿Track Name;Artist Name(s);Album Name\r\nLa Flaca;Jarabe de Palo;La Flaca\r\n\r\nFlaca;Andrés Calamaro;Alta Suciedad\r\n"
        val rows = PlaylistTextParser.parse(csv).rows
        assertEquals(listOf(
            ImportedRow("La Flaca", "Jarabe de Palo", "La Flaca"),
            ImportedRow("Flaca", "Andrés Calamaro", "Alta Suciedad"),
        ), rows)
    }

    @Test
    fun parsesQuotedFieldWithNewline() {
        val records = PlaylistTextParser.parseCsvRecords("a,\"b\nc\",d\n1,2,3", ',')
        assertEquals(listOf(listOf("a", "b\nc", "d"), listOf("1", "2", "3")), records)
    }

    @Test
    fun parsesPastedTextLines() {
        val text = """
            # Mi lista
            Soda Stereo - De Música Ligera
            1. Juanes – La Camisa Negra
            Carlos Vives - La Gota Fría - En Vivo
            Solo un título
        """.trimIndent()
        val rows = PlaylistTextParser.parse(text).rows
        assertEquals(4, rows.size)
        assertEquals(ImportedRow("De Música Ligera", "Soda Stereo"), rows[0])
        assertEquals(ImportedRow("La Camisa Negra", "Juanes"), rows[1])
        assertEquals(ImportedRow("La Gota Fría - En Vivo", "Carlos Vives"), rows[2])
        assertEquals(ImportedRow("Solo un título", ""), rows[3])
    }

    @Test
    fun emptyInputGivesNoRows() {
        assertTrue(PlaylistTextParser.parse("   \n  ").rows.isEmpty())
    }
}
