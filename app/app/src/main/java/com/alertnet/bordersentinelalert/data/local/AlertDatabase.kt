package com.alertnet.bordersentinelalert.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.alertnet.bordersentinelalert.data.local.dao.AlertDao
import com.alertnet.bordersentinelalert.data.local.entity.AlertEntity

@Database(entities = [AlertEntity::class], version = 1, exportSchema = false)
abstract class AlertDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
}
