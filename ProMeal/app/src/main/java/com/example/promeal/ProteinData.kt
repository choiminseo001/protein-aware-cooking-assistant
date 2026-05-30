package com.example.promeal

object ProteinData {
    val proteinMap = mapOf(
        "bay_leaf" to 0,
        "bell_pepper" to 1,
        "broccoli" to 3,
        "cabbage" to 1,
        "carrot" to 1,
        "cauliflower" to 2,

        "chicken" to 25,
        "chickpeas" to 7,

        "coriander" to 0,
        "cucumber" to 1,

        "egg" to 6,

        "eggplant" to 1,

        "fish" to 22,

        "garlic" to 0,
        "ginger" to 0,

        "kumquat" to 0,
        "lemon" to 0,

        "long_pepper" to 0,

        "mutton" to 25,

        "okra" to 1,

        "onion" to 1,

        "pork" to 22,

        "potato" to 2,
        "pumpkin" to 1,
        "radish" to 1,

        "salt" to 0,

        "shrimp" to 20,

        "small_pepper" to 0,

        "spring_onion" to 1,

        "tofu" to 12,      // 반 모 기준

        "tomato" to 1,

        "turmeric" to 0,

        // 추가하면 좋은 단백질 재료
        "beef" to 26,
        "ground_beef" to 25,
        "turkey" to 29,
        "salmon" to 22,
        "tuna" to 24,
        "crab" to 18,
        "squid" to 16,
        "octopus" to 15,

        "beans" to 7,
        "black_beans" to 8,
        "kidney_beans" to 8,
        "lentils" to 9,
        "edamame" to 11,

        "tempeh" to 19,
        "cheese" to 7,      // 슬라이스 2장 정도
        "greek_yogurt" to 10,
        "milk" to 8         // 한 컵(240ml)
    )

    fun getProtein(foodName: String): Int {
        return proteinMap[foodName.lowercase()] ?: 0
    }
}