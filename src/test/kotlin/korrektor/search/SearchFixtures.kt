package korrektor.search

import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word

/** A page whose lines are [texts] (ids `l1`, `l2`, …, one region), with one word per space-separated word. */
fun layerOf(
    vararg texts: String,
    number: Int = 1,
): PageLayer =
    PageLayer(
        number,
        100.0,
        200.0,
        listOf(
            Region(
                "r1",
                RegionType.TEXT,
                1,
                null,
                texts.mapIndexed { i, text ->
                    Line("l${i + 1}", null, null, if (text.isEmpty()) emptyList() else text.split(' ').map(::Word))
                },
            ),
        ),
    )

/** The compiled regex of [query], which the test says must be valid. */
fun regexOf(query: SearchQuery): Regex = (query.compile() as Compiled.Ready).regex
