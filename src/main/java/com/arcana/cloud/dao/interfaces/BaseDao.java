package com.arcana.cloud.dao.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Base DAO interface providing common CRUD operations.
 * This abstraction allows switching between different database implementations
 * (MySQL, PostgreSQL, MongoDB) and ORM strategies (MyBatis, JPA).
 *
 * @param <T>  Entity type
 * @param <ID> Entity ID type
 */
public interface BaseDao<T, ID> {

    /**
     * Save an entity (create or update).
     *
     * @param entity the entity to save
     * @return the saved entity
     */
    T save(T entity);

    /**
     * Save all entities.
     *
     * @param entities the entities to save
     * @return the saved entities
     */
    List<T> saveAll(Iterable<T> entities);

    /**
     * Find entity by ID.
     *
     * @param id the entity ID
     * @return an Optional containing the entity if found
     */
    Optional<T> findById(ID id);

    /**
     * Check if entity exists by ID.
     *
     * @param id the entity ID
     * @return true if entity exists
     */
    boolean existsById(ID id);

    /**
     * Find all entities.
     *
     * @return list of all entities
     */
    List<T> findAll();

    /**
     * Find all entities with pagination.
     *
     * @param pageable pagination information
     * @return page of entities
     */
    Page<T> findAll(Pageable pageable);

    /**
     * Count all entities.
     *
     * @return count of all entities
     */
    long count();

    /**
     * Delete entity by ID.
     *
     * @param id the entity ID
     */
    void deleteById(ID id);

    /**
     * Delete entity.
     *
     * @param entity the entity to delete
     */
    void delete(T entity);

    /**
     * Delete all entities.
     */
    void deleteAll();

    /**
     * Delete all provided entities.
     *
     * @param entities the entities to delete
     */
    void deleteAll(Iterable<T> entities);
}
