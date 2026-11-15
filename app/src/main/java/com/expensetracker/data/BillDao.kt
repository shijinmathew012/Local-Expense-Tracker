package com.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY isPaid ASC, dueDateTimestamp ASC")
    fun getAllBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE isPaid = 0 ORDER BY dueDateTimestamp ASC")
    fun getPendingBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE isPaid = 0 AND (LOWER(name) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(category) LIKE '%' || LOWER(:keyword) || '%') LIMIT 1")
    suspend fun findPendingBillByKeyword(keyword: String): BillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: BillEntity): Long

    @Update
    suspend fun update(bill: BillEntity)

    @Delete
    suspend fun delete(bill: BillEntity)

    @Query("UPDATE bills SET isPaid = 1, paidTimestamp = :paidTime WHERE id = :billId")
    suspend fun markAsPaid(billId: Long, paidTime: Long = System.currentTimeMillis())
}
