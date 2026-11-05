package online.taranov.persista

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class CallsTile : TileService() {
    override fun onStartListening() = update()

    override fun onClick() {
        if (!Calls.isOn(this)) {
            Calls.resume(this)
            update()
            return
        }
        val show = { showDialog(Calls.chooser(this) { update() }) }
        if (isLocked) unlockAndRun(show) else show()
    }

    private fun update() {
        val tile = qsTile ?: return
        val on = Calls.isOn(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (on) getString(R.string.tile_on) else getString(R.string.tile_until, Calls.time(Calls.pausedUntil(this)))
        tile.updateTile()
    }
}
