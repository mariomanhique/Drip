package com.mariomanhique.drip.data

import com.mariomanhique.drip.domain.Battle
import com.mariomanhique.drip.domain.BattleStatus
import com.mariomanhique.drip.domain.Rules
import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.Challenge
import com.mariomanhique.drip.domain.Comment
import com.mariomanhique.drip.domain.Constraint
import com.mariomanhique.drip.domain.DripState
import com.mariomanhique.drip.domain.Outfit
import com.mariomanhique.drip.domain.PointEvent
import com.mariomanhique.drip.domain.User

object Seed {
    const val DEMO_PASSWORD = "drip123"

    private val users = listOf(
        User("u1", "ama", "Ama Osei", "Streetwear and sneakers.", setOf(Category.STREETWEAR), "GH", 1, DEMO_PASSWORD, votesCast = 100, city = "Porto"),
        User("u2", "lucia", "Lucía Marín", "Vintage hunter.", setOf(Category.VINTAGE), "ES", 2, DEMO_PASSWORD, votesCast = 100, city = "Porto"),
        User("u3", "kofi", "Kofi Mensah", "Clean lines only.", setOf(Category.MINIMALIST), "GH", 3, DEMO_PASSWORD, votesCast = 100, city = "Porto"),
        User("u4", "sana", "Sana Iqbal", "Colour is a personality.", setOf(Category.COLOUR), "UK", 4, DEMO_PASSWORD, votesCast = 100, city = "Lisboa"),
        User("u5", "tomas", "Tomás Silva", "Suits on weekdays.", setOf(Category.FORMAL), "PT", 5, DEMO_PASSWORD, votesCast = 100, city = "Porto"),
        User("u6", "mei", "Mei Tanaka", "Accessories maximalist.", setOf(Category.ACCESSORIES), "JP", 6, DEMO_PASSWORD, votesCast = 100, city = "Lisboa"),
    ) + listOf(
        tester("u7", "rui", "Rui Costa", Category.STREETWEAR, "Porto", 7),
        tester("u8", "ines", "Inês Lopes", Category.VINTAGE, "Porto", 8),
        tester("u9", "joao", "João Pinto", Category.CASUAL, "Porto", 9),
        tester("u10", "marta", "Marta Sousa", Category.MINIMALIST, "Porto", 10),
        tester("u11", "pedro", "Pedro Alves", Category.FORMAL, "Porto", 11),
        tester("u12", "bea", "Beatriz Faria", Category.COLOUR, "Porto", 12),
        tester("u13", "diogo", "Diogo Matos", Category.STREETWEAR, "Porto", 13),
        tester("u14", "carla", "Carla Nunes", Category.ACCESSORIES, "Porto", 14),
        tester("u15", "hugo", "Hugo Reis", Category.CASUAL, "Porto", 15),
        tester("u16", "sofia", "Sofia Rocha", Category.VINTAGE, "Porto", 16),
        tester("u17", "tiago", "Tiago Moura", Category.MINIMALIST, "Porto", 17),
        tester("u18", "ana", "Ana Duarte", Category.COLOUR, "Lisboa", 18),
        tester("u19", "nuno", "Nuno Pires", Category.STREETWEAR, "Lisboa", 19),
        tester("u20", "leila", "Leila Amade", Category.CASUAL, "Porto", 20),
    )

    private fun tester(id: String, handle: String, name: String, cat: Category, city: String, order: Int) =
        User(id, handle, name, "Test account.", setOf(cat), "PT", order, DEMO_PASSWORD, votesCast = 100, city = city)

    private fun o(
        id: String, author: String, caption: String, cat: Category, emoji: String,
        palette: List<Long>, order: Int, challenge: String? = null, props: Int = 0, comments: List<Comment> = emptyList(),
    ) = Outfit(
        id, author, caption, cat, palette, emoji, order.toLong(), challenge,
        propsFrom = (1..props).map { "fan$it" }.toSet(), comments = comments,
    )

    private val outfits = listOf(
        o("o1", "u1", "Oversized hoodie, cargo and fresh kicks", Category.STREETWEAR, "🧢", listOf(0xFF1F2937, 0xFF6B7280, 0xFFF59E0B), 1, "c1", 7),
        o("o2", "u2", "70s corduroy find from the flea market", Category.VINTAGE, "🕰️", listOf(0xFF92400E, 0xFFD97706, 0xFFFDE68A), 2, "c1", 5,
            listOf(Comment("cm1", "u1", "That jacket is unreal 🔥"))),
        o("o3", "u3", "All-white layering, nothing more", Category.MINIMALIST, "⬜", listOf(0xFFF9FAFB, 0xFFE5E7EB, 0xFF9CA3AF), 3, null, 4),
        o("o4", "u4", "Head-to-toe cobalt", Category.COLOUR, "🎨", listOf(0xFF1D4ED8, 0xFF3B82F6, 0xFFBFDBFE), 4, "c2", 6),
        o("o5", "u5", "Navy three-piece, brown oxfords", Category.FORMAL, "🤵", listOf(0xFF1E3A8A, 0xFF78350F, 0xFFE5E7EB), 5, "c3", 3),
        o("o6", "u6", "Stacked rings and a vintage watch", Category.ACCESSORIES, "⌚", listOf(0xFFB45309, 0xFFFCD34D, 0xFF111827), 6, null, 8),
        o("o7", "u1", "Black never fails", Category.STREETWEAR, "🖤", listOf(0xFF000000, 0xFF27272A, 0xFF52525B), 7, "c2", 4),
        o("o8", "u3", "Linen set, sand tones", Category.MINIMALIST, "🌾", listOf(0xFFD6C6A5, 0xFFEADFC8, 0xFFA8977A), 8, "c2", 2),
        o("o9", "u2", "Denim on denim, but make it soft", Category.CASUAL, "👖", listOf(0xFF60A5FA, 0xFF93C5FD, 0xFFDBEAFE), 9, "c4", 9),
        o("o10", "u4", "Double denim with a red scarf", Category.CASUAL, "🧣", listOf(0xFF1E40AF, 0xFFDC2626, 0xFF93C5FD), 10, "c4", 6),
        o("o11", "u5", "Light denim, white sneakers", Category.CASUAL, "👟", listOf(0xFFBFDBFE, 0xFFF9FAFB, 0xFF6B7280), 11, "c4", 2),
        o("o12", "u6", "Chunky chains over a plain tee", Category.ACCESSORIES, "⛓️", listOf(0xFF111827, 0xFFE5E7EB, 0xFFFACC15), 12, null, 3),
        o("o13", "u7", "Tester look #1", Category.STREETWEAR, "🧥", listOf(0xFF111827, 0xFF4B5563, 0xFFFBBF24), 13, "c1", 0),
        o("o14", "u8", "Tester look #2", Category.VINTAGE, "🕶️", listOf(0xFF7C2D12, 0xFFFB923C, 0xFFFEF3C7), 14, "c1", 1),
        o("o15", "u9", "Tester look #3", Category.CASUAL, "👖", listOf(0xFF1E3A8A, 0xFF60A5FA, 0xFFE0F2FE), 15, "c1", 2),
        o("o16", "u10", "Tester look #4", Category.MINIMALIST, "⬜", listOf(0xFFF3F4F6, 0xFFD1D5DB, 0xFF6B7280), 16, "c1", 3),
        o("o17", "u11", "Tester look #5", Category.FORMAL, "🤵", listOf(0xFF0F172A, 0xFF334155, 0xFFE2E8F0), 17, "c2", 4),
        o("o18", "u12", "Tester look #6", Category.COLOUR, "🎨", listOf(0xFFBE185D, 0xFFF472B6, 0xFFFCE7F3), 18, "c2", 5),
        o("o19", "u13", "Tester look #7", Category.STREETWEAR, "🧥", listOf(0xFF111827, 0xFF4B5563, 0xFFFBBF24), 19, null, 6),
        o("o20", "u14", "Tester look #8", Category.ACCESSORIES, "🕶️", listOf(0xFF7C2D12, 0xFFFB923C, 0xFFFEF3C7), 20, null, 7),
    )

    private val challenges = listOf(
        Challenge("c1", "Sneakers Only", "Build the look around your best pair of sneakers.",
            "One outfit per person. Sneakers must be visible.", Category.STREETWEAR),
        Challenge("c2", "One Colour, One Outfit", "Pick a single colour and commit.",
            "Tones of one colour only; neutrals allowed as accents.", Category.COLOUR, constraint = Constraint.CLOSET),
        Challenge("c3", "Business Casual", "Smart without the stiffness.",
            "Office-appropriate. No full suits.", Category.FORMAL, sponsor = "Atelier Nord", constraint = Constraint.BUDGET),
        Challenge("c4", "Denim Challenge", "Show us your best denim look.",
            "At least one denim piece.", Category.CASUAL, closed = true, constraint = Constraint.THRIFT),
    )

    private fun battles(now: Long) = listOf(
        Battle(
            "b1", "u4", "o4", "o3", BattleStatus.VOTING,
            createdAt = now - 12 * Rules.HOUR_MS, matchedAt = now - 12 * Rules.HOUR_MS, endsAt = now + 12 * Rules.HOUR_MS,
        ),
        Battle("b2", "u6", "o6", createdAt = now - Rules.HOUR_MS),
        // Closed showdown so share buttons can be tried straight away as "ama" (u1) or "rui" (u2).
        Battle(
            "b3", "u1", "o1", "o2", BattleStatus.CLOSED,
            createdAt = now - 3 * Rules.DAY_MS, matchedAt = now - 3 * Rules.DAY_MS, endsAt = now - 2 * Rules.DAY_MS,
            winnerOutfitId = "o1",
        ),
    )

    private val ledger = listOf(
        PointEvent("u2", 25, "1st place · Denim Challenge"),
        PointEvent("u4", 15, "2nd place · Denim Challenge"),
        PointEvent("u5", 10, "3rd place · Denim Challenge"),
        PointEvent("u1", 10, "Won a showdown"),
        PointEvent("u6", 10, "Won a showdown"),
        PointEvent("u3", 5, "Challenge entry"),
    )

    private val follows = setOf(
        "u1" to "u2", "u2" to "u1", "u3" to "u1", "u4" to "u1", "u5" to "u2", "u6" to "u4",
    )

    fun state(now: Long) = DripState(
        users = users, outfits = outfits, challenges = challenges, battles = battles(now),
        follows = follows, ledger = ledger,
    )
}
