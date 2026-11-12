package online.taranov.persista

// Runs on the JVM with the project's existing Kotlin compiler/runtime; no Android or test SDK is needed.
object AlarmStateChecks {
    @JvmStatic fun main(args: Array<String>) {
        var count = 0
        fun test(name: String, run: () -> Unit) { run(); count++; println("PASS $name") }
        fun entry(id: String, token: String = "generation-1", due: Long = 301_000) =
            SnoozeEntry(AlarmSession(id, "Test $id", "Test only", "content://test/$id", "Test app", "test.app", "key-$id"),
                token, due, 7)
        val a = entry("a")
        val b = entry("b", due = 302_000)

        test("No early repeat, delivery is consumed once") {
            val state = SnoozeLedger().put(a)
            check(state.deliver("a", a.token, 300_999) == state)
            val ready = state.deliver("a", a.token, 301_000)
            check(ready.nextReady() == a.copy(phase = SnoozePhase.READY))
            check(ready.deliver("a", a.token, 301_001) == ready)
            val playing = ready.claim("a", a.token)
            check(playing.nextReady() == null)
            check(playing.claim("a", a.token) == playing)
            check(playing.deliver("a", a.token, 999_999) == playing)
        }
        test("Independent reminders preserve contents and melody") {
            val state = SnoozeLedger().put(a).put(b).deliver("a", a.token, a.dueElapsed)
            check(state.entries.size == 2)
            check(state.find("b", b.token) == b)
            check(state.nextReady()?.session == a.session)
        }
        test("A new snooze generation survives old actions and old completion") {
            val newer = a.copy(token = "generation-2", dueElapsed = 601_000)
            val state = SnoozeLedger().put(a).put(b).put(newer)
            check(state.entries.size == 2)
            check(state.remove("a", a.token) == state)
            check(state.deliver("a", a.token, 900_000) == state)
            check(state.claim("a", a.token) == state)
            check(state.find("a", newer.token) == newer)
        }
        test("Cancelled reminders cannot be delivered and do not cancel siblings") {
            val state = SnoozeLedger().put(a).put(b).remove("a", a.token)
            check(state.deliver("a", a.token, 999_999) == state)
            check(state.find("b", b.token) == b)
            check(state.nextReady() == null)
            val cleared = SnoozeLedger()
            check(cleared.deliver("b", b.token, 999_999).entries.isEmpty())
        }
        test("Ready reminders are claimed in deadline order with one claim each") {
            var state = SnoozeLedger().put(b).put(a)
                .deliver("b", b.token, 400_000).deliver("a", a.token, 400_000)
            check(state.nextReady()?.session?.id == "a")
            state = state.claim("a", a.token)
            check(state.nextReady()?.session?.id == "b")
            state = state.claim("b", b.token)
            check(state.nextReady() == null)
        }
        test("Elapsed five-minute interval survives wall clock changes") {
            check(a.wallTime(1_000_000, 1_000) == 1_300_000L)
            check(a.wallTime(9_000_000, 101_000) == 9_200_000L)
            check(a.wallTime(500_000, 201_000) == 600_000L)
            check(a.wallTime(500_000, 401_000) == 500_000L)
        }
        test("A failed write never arms an OS alarm and rolls back the snapshot") {
            val before = SnoozeLedger().put(b)
            var state = before
            var writes = 0
            var armed = false
            val result = SnoozeTransaction.plan(before, a, {
                state = it; ++writes != 1 // Simulate commit's changed memory but failed disk write.
            }, { armed = true }, { error("Nothing should be armed") })
            check(!result && !armed && state == before && writes == 2)
        }
        test("An AlarmManager failure disarms the new intent and restores siblings") {
            val before = SnoozeLedger().put(a).put(b)
            val newer = a.copy(token = "generation-2")
            var state = before
            val disarmed = mutableListOf<SnoozeEntry>()
            val result = SnoozeTransaction.plan(before, newer, { state = it; true }, {
                check(state.find("a", newer.token) == newer) // Snapshot must exist before scheduling.
                throw SecurityException("Test: permission revoked")
            }, { disarmed.add(it) })
            check(!result && state == before && disarmed == listOf(newer))
        }
        test("Successful re-snooze replaces only its own old system alarm") {
            val before = SnoozeLedger().put(a).put(b)
            val newer = a.copy(token = "generation-2")
            var state = before
            val events = mutableListOf<String>()
            val result = SnoozeTransaction.plan(before, newer, {
                state = it; events.add("write"); true
            }, { events.add("arm:${it.token}") }, { events.add("disarm:${it.token}") })
            check(result && state.entries.size == 2)
            check(state.find("b", b.token) == b)
            check(events == listOf("write", "arm:generation-2", "disarm:generation-1"))
        }
        println("$count alarm state checks passed")
    }
}
