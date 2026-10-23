package com.iot.app.repository;

import com.iot.app.model.LightLinkMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LightLinkMapRepository extends JpaRepository<LightLinkMap, Integer> {
}
