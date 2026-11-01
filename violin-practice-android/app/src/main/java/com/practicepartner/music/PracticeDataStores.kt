package com.practicepartner.music

interface PracticeDataStore {
    fun load(
        profile: ChildProfile,
        currentDate: String = currentDateKey(),
    ): PracticeSnapshot

    fun loadWithStatus(
        profile: ChildProfile,
        currentDate: String = currentDateKey(),
    ): PracticeLoadResult

    fun save(profile: ChildProfile, snapshot: PracticeSnapshot)

    fun delete(profile: ChildProfile)

    fun referencedRecordingPaths(profiles: List<ChildProfile>): Set<String>
}

interface FamilyDataStore : WorkbookSyncSettings {
    fun loadHousehold(): Household

    fun saveHousehold(household: Household)

    override fun saveChildProfile(profile: ChildProfile): Household

    fun addChildProfile(profile: ChildProfile): Household

    fun setActiveChildProfile(childProfileId: String): Household

    fun removeChildProfile(childProfileId: String): Household

    fun hasAdultPin(): Boolean

    fun setAdultPin(pin: String): Boolean

    fun verifyAdultPin(pin: String): Boolean
}
