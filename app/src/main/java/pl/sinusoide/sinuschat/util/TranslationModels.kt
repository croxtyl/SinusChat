package pl.sinusoide.sinuschat.util

object TranslationModels {

    const val BASE_URL = "https://files.sinusoide.pl/api/models/translate/"

    class Part(val name: String, val sha256: String, val size: Long, val download: Long)

    class Pair(val from: String, val to: String, val model: Part, val vocab: Part, val lex: Part?) {
        val code get() = from + to
        val parts get() = listOfNotNull(model, vocab, lex)
    }

    val PAIRS: List<Pair> = listOf(
        Pair("pl", "en",
            Part("model.plen.intgemm.alphas.bin", "06dbab3baa3bbceab746bc6a3bba807878f38536382c03acddf59d3edf3b5d94", 31561787L, 22625705L),
            Part("vocab.plen.spm", "2000f182efa8ce5f69bc3f9e574666b5bc2c0af684eedd32f69d15aa9021ea85", 821612L, 413076L),
            Part("lex.50.50.plen.s2t.bin", "b295dfefa7022a62e105276e8c39fdfd67bffe588f4fd78583edd4e04ee0cb3b", 4964208L, 2420025L)),
        Pair("en", "pl",
            Part("model.enpl.intgemm.alphas.bin", "b0b59716bcc8f46b8dc6ab5c32a2732acaad42a5a6d9ae9b472526a1bb0d71d4", 31561787L, 22567926L),
            Part("vocab.enpl.spm", "2000f182efa8ce5f69bc3f9e574666b5bc2c0af684eedd32f69d15aa9021ea85", 821612L, 413076L),
            Part("lex.50.50.enpl.s2t.bin", "d7d7b364673f633763c9f8f2e8ee9c7a184e92f4086e8b84abe870a2a8dbe5d5", 3540052L, 1830876L)),
        Pair("cs", "en",
            Part("model.csen.intgemm.alphas.bin", "c815273b31074492eaab0ccd78d33d7ac16f94088707ab2492abaedcb9ee0b75", 31561787L, 22824837L),
            Part("vocab.csen.spm", "ee4b8ce15c8e1237c5f7a2d04741341beb0a5d5c09820f7ec08f13ee2b212755", 815154L, 412352L),
            Part("lex.50.50.csen.s2t.bin", "eba6bd9575251525f1941640ea0b6527a06e72a46421579eafea04d635ee20a8", 4990588L, 2423705L)),
        Pair("en", "cs",
            Part("model.encs.intgemm.alphas.bin", "1d7855f91c3e2b8ef79fbea62707d46ceb8a1ad01834ef6dd500f32a7006cc2c", 31561787L, 23184140L),
            Part("vocab.encs.spm", "ee4b8ce15c8e1237c5f7a2d04741341beb0a5d5c09820f7ec08f13ee2b212755", 815154L, 412352L),
            Part("lex.50.50.encs.s2t.bin", "699b9b59fe3acbcddf831380024b2e139f2cbe225dce96d59ade2b159318f58b", 3572436L, 1853080L)),
        Pair("uk", "en",
            Part("model.uken.intgemm.alphas.bin", "2e7b8bc9d2569d8b4ae244e964cdc343a0aa3735cc83ecb664be84a37cb45f48", 17141051L, 12679158L),
            Part("vocab.uken.spm", "8b51480dfc4ca2d7a08d2bfdaa9e57353ca97e6cf3d5f9aba4c0179d3665bfc0", 886410L, 412818L),
            Part("lex.50.50.uken.s2t.bin", "32b5e0df493671a223cb5cb32430181cbb7fe6b5562c91dbc3d35410d37e5ff6", 3914120L, 1743815L)),
        Pair("en", "uk",
            Part("model.enuk.intgemm.alphas.bin", "ed8bb4a811e1c45a259e91052080d25e5c8b4e2df0e59cb1521c04a57855ef97", 31561787L, 22858090L),
            Part("vocab.enuk.spm", "4dd6a91feff71e745395dcc0d52f885848ca0d843d5f70628ca51b3be1b70b45", 922077L, 434171L),
            Part("lex.50.50.enuk.s2t.bin", "dbfd4f8dc95da9c575164f7fc95ef00a2fb5f7d9f51b656559f99eeebdb57c87", 3054364L, 1525471L)),
        Pair("de", "en",
            Part("model.deen.intgemm.alphas.bin", "3e6f7c2c2425d10824797270b382bee718ff34af2cab9308841c82ca46dc6f20", 31561787L, 22397635L),
            Part("vocab.deen.spm", "69f730becafa48e3bb2c244eab66456877c08959a02f2bd5519b5a3088b62f9c", 810073L, 413337L),
            Part("lex.50.50.deen.s2t.bin", "113b98460468360cca68c042e1cddf49c4e1931cbb975ed04349c9a3bd607010", 4945796L, 2560314L)),
        Pair("en", "de",
            Part("model.ende.intgemm.alphas.bin", "8df29d9494d19f47fd5d97c6a73474c6f657e9f81c1a607c431d02befdf3810f", 31561787L, 22988585L),
            Part("vocab.ende.spm", "69f730becafa48e3bb2c244eab66456877c08959a02f2bd5519b5a3088b62f9c", 810073L, 413337L),
            Part("lex.50.50.ende.s2t.bin", "7ed39f1cffbd68a27ddf05bbfe068de2060f1d7e69f1a20e27ae923551dd7393", 4347672L, 2263559L))
    )

    fun pair(from: String, to: String): Pair? = PAIRS.firstOrNull { it.from == from && it.to == to }

    fun pairsFor(lang: String): List<Pair> = PAIRS.filter { it.from == lang || it.to == lang }
}
