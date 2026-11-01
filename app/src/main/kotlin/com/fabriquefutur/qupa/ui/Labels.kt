package com.fabriquefutur.qupa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fabriquefutur.qupa.R
import com.fabriquefutur.qupa.game.Difficulty

/** Category names by Open Trivia Database id; the strings live in the per-language strings.xml files. */
private val CategoryNames: Map<Int, Int> = mapOf(
    9 to R.string.cat_9,
    10 to R.string.cat_10,
    11 to R.string.cat_11,
    12 to R.string.cat_12,
    13 to R.string.cat_13,
    14 to R.string.cat_14,
    15 to R.string.cat_15,
    16 to R.string.cat_16,
    17 to R.string.cat_17,
    18 to R.string.cat_18,
    19 to R.string.cat_19,
    20 to R.string.cat_20,
    21 to R.string.cat_21,
    22 to R.string.cat_22,
    23 to R.string.cat_23,
    24 to R.string.cat_24,
    25 to R.string.cat_25,
    26 to R.string.cat_26,
    27 to R.string.cat_27,
    28 to R.string.cat_28,
    29 to R.string.cat_29,
    30 to R.string.cat_30,
    31 to R.string.cat_31,
    32 to R.string.cat_32,
)

@Composable
fun categoryName(id: Int): String = CategoryNames[id]?.let { stringResource(it) } ?: ""

@Composable
fun difficultyName(d: Difficulty?): String = stringResource(
    when (d) {
        null -> R.string.difficulty_any
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
    },
)
