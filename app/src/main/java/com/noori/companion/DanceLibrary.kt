package com.noori.companion

enum class DanceCategory(
    val displayName: String,
    val isCouple: Boolean = false,
    val tempoMs: Int = 650
) {
    BOLLYWOOD("1. Bollywood Dance", false, 600),
    HIP_HOP("2. Hip-Hop Dance", false, 520),
    FREESTYLE("3. Freestyle Dance", false, 680),
    CLASSICAL_INDIAN("4. Classical Indian Dance", false, 850),
    PUNJABI("5. Punjabi Dance", false, 480),
    HARYANVI("6. Haryanvi Dance", false, 540),
    GARBA("7. Garba Dance", false, 700),
    K_POP("8. K-Pop Dance", false, 500),
    SLOW("9. Slow Dance", false, 1100),
    CUTE_ANIME("10. Cute Anime Dance", false, 580),
    ROMANTIC_COUPLE("11. Romantic Couple Dance", true, 950);

    companion object {
        fun matchFromCommand(text: String): DanceCategory? {
            val lower = text.lowercase()
            return when {
                lower.contains("couple") || lower.contains("मेरे साथ") || lower.contains("saath") -> ROMANTIC_COUPLE
                lower.contains("bollywood") -> BOLLYWOOD
                lower.contains("hip-hop") || lower.contains("hip hop") || lower.contains("hiphop") -> HIP_HOP
                lower.contains("freestyle") -> FREESTYLE
                lower.contains("classical") || lower.contains("शास्त्रीय") -> CLASSICAL_INDIAN
                lower.contains("punjabi") || lower.contains("bhangra") -> PUNJABI
                lower.contains("haryanvi") -> HARYANVI
                lower.contains("garba") -> GARBA
                lower.contains("k-pop") || lower.contains("kpop") -> K_POP
                lower.contains("slow") -> SLOW
                lower.contains("anime") || lower.contains("cute") -> CUTE_ANIME
                lower.contains("dance") || lower.contains("नाच") || lower.contains("डांस") -> BOLLYWOOD
                else -> null
            }
        }
    }
}
