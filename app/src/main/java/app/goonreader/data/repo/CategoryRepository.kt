package app.goonreader.data.repo

import app.goonreader.data.db.CategoryDao
import app.goonreader.data.db.CategoryEntity
import app.goonreader.data.db.CategoryRow
import app.goonreader.data.db.WorkCategoryCrossRefEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** User categories (PRD 9.2/9.7). The join table and entity already existed from M2. */
@Singleton
class CategoryRepository @Inject constructor(private val dao: CategoryDao) {

    fun observeAll(): Flow<List<CategoryRow>> = dao.observeWithCounts()

    /** Ids of the categories a work belongs to, for the details dialog. */
    fun observeIdsFor(workId: String): Flow<List<Long>> = dao.observeCategoryIds(workId)

    /** Returns the new id, or -1 when the name is blank or already exists. */
    suspend fun create(name: String): Long {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) -1L else dao.insert(CategoryEntity(name = trimmed))
    }

    suspend fun delete(categoryId: Long) = dao.delete(categoryId)

    suspend fun setAssigned(workId: String, categoryId: Long, assigned: Boolean) {
        if (assigned) {
            dao.assign(WorkCategoryCrossRefEntity(workId = workId, categoryId = categoryId))
        } else {
            dao.unassign(workId, categoryId)
        }
    }
}
