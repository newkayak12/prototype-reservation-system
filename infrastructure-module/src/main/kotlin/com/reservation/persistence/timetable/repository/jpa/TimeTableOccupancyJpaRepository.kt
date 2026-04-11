package com.reservation.persistence.timetable.repository.jpa

import com.reservation.persistence.timetable.entity.TimeTableOccupancyEntity
import org.springframework.data.repository.CrudRepository

interface TimeTableOccupancyJpaRepository : CrudRepository<TimeTableOccupancyEntity, String>
