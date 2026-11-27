package app.aidroid.remaster.ai

/**
 * Conservative guard for agent tools that can retrieve external material or execute submitted code.
 * This is a product safety boundary, not a substitute for provider or server-side safeguards.
 */
object AgentSafetyPolicy {
    private val restrictedTerms = listOf(
        Regex("(?iu)\\b(firearms?|guns?|rifles?|pistols?|ammunition|ammo|explosives?|grenades?|detonators?|switchblades?|tactical knives|tasers?|pepper spray|mace|brass knuckles|stun guns?|bomb making|weapon construction)\\b"),
        Regex("(?iu)\\b(recreational drugs?|cocaine|heroin|fentanyl|methamphetamine|meth|mdma|ecstasy|marijuana|cannabis|thc|vapes?|vaping|nicotine pouches|get high|drug dealer)\\b"),
        Regex("(?iu)\\b(betting|sports betting|sportsbook|bookmakers?|gambling|online casino|slot machines?|prediction markets?|polymarket|wagering)\\b"),
        Regex("(?iu)\\b(choking challenge|blackout challenge|fire challenge|dangerous challenge|dangerous stunt|breath holding challenge)\\b"),
        Regex("(?iu)(оруж\\w*|огнестрел\\w*|пистолет\\w*|винтов\\w*|ружь\\w*|патрон\\w*|боеприпас\\w*|взрывчат\\w*|гранат\\w*|детонатор\\w*|мина\\w*|нож\\w*|кастет\\w*|электрошок\\w*|перцов\\w* баллончик\\w*|самооборон\\w*)"),
        Regex("(?iu)(наркот\\w*|кокаин\\w*|героин\\w*|фентанил\\w*|метамфетамин\\w*|марихуан\\w*|каннабис\\w*|вейп\\w*|никотин\\w*|алкогол\\w*)"),
        Regex("(?iu)(букмекер\\w*|ставк\\w*|казино\\w*|слот\\w*|гемблинг\\w*|беттинг\\w*|полимаркет\\w*|рын\\w* предсказан\\w*)"),
        Regex("(?iu)(опасн\\w* челлендж|опасн\\w* испытан\\w*|челлендж\\w* удушен\\w*|испытан\\w* с удушен\\w*)"),
        Regex("(?iu)(как\\s+(сделать|собрать|приготовить|использовать|достать|купить).{0,80}(оруж\\w*|взрывчат\\w*|наркот\\w*|вейп\\w*|казино|ставк\\w*))")
    )

    fun validateSearchQuery(query: String) {
        require(query.isNotBlank()) { "Поисковый запрос пуст." }
        if (restrictedTerms.any { it.containsMatchIn(query) }) {
            throw IllegalArgumentException(
                "Этот запрос не отправлен во внешний поиск. Могу помочь с безопасной общей информацией или другой темой."
            )
        }
    }

    fun validateCode(code: String) {
        if (restrictedTerms.any { it.containsMatchIn(code) }) {
            throw IllegalArgumentException(
                "Этот фрагмент кода не будет отправлен на внешний сервер выполнения. Попробуй безопасный учебный или рабочий пример."
            )
        }
    }
}
