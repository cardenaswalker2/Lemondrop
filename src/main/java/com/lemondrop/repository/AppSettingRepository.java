package com.lemondrop.repository;

import com.lemondrop.model.AppSetting;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface AppSettingRepository extends MongoRepository<AppSetting, String> {
    Optional<AppSetting> findByKey(String key);
    List<AppSetting> findByCategoryOrderByKeyAsc(String category);
}
