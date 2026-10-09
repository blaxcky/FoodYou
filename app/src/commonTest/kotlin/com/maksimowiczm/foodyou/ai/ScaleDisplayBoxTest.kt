package com.maksimowiczm.foodyou.ai

import kotlin.test.*

class ScaleDisplayBoxTest {
    @Test fun acceptsActualGemmaObjectsListsAndFences() {
        val expected = ScaleDisplayBox(465, 626, 563, 786)
        for (text in listOf(
            """{"box_2d":[465,626,563,786]}""",
            """{"value":19,"unit":"g","box_2d":[465,626,563,786]}""",
            """[{"box_2d":[465,626,563,786],"label":"the kitchen scale's digital weight display"}]""",
            "```json\n[{\"box_2d\":[465,626,563,786]}]\n```",
            "```\n{\"box_2d\":[465.9,626.2,563.1,786.9]}\n```",
        )) assertEquals(expected, parseScaleDisplayBox(text), text)
    }

    @Test fun rejectsMissingNonNumericOutOfBoundsAndReversedCoordinates() {
        for (text in listOf(
            "not json", "{}", "[]", """{"box_2d":null}""",
            """{"value":null,"box_2d":[465,626,563,786]}""",
            """{"box_2d":[465,626,563]}""",
            """{"box_2d":[465,626,563,786,900]}""",
            """{"box_2d":["465",626,563,786]}""",
            """{"box_2d":[465,true,563,786]}""",
            """{"box_2d":[465,null,563,786]}""",
            """{"box_2d":[-1,626,563,786]}""",
            """{"box_2d":[465,626,1001,786]}""",
            """{"box_2d":[465,626,563,1e309]}""",
            """{"box_2d":[563,626,465,786]}""",
            """{"box_2d":[465,786,563,626]}""",
            """{"box_2d":[465,626,465,786]}""",
        )) assertNull(parseScaleDisplayBox(text), text)
    }

    @Test fun rejectsTinyAndWholeSceneBoxesButAllowsDisplaysAtImageEdges() {
        assertNull(parseScaleDisplayBox("""{"box_2d":[0,0,1,1]}"""))
        assertNull(parseScaleDisplayBox("""{"box_2d":[0,0,1000,1000]}"""))
        assertEquals(ScaleDisplayBox(800,800,1000,1000),
            parseScaleDisplayBox("""{"box_2d":[800,800,1000,1000]}"""))
    }
}
