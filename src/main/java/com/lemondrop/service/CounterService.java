package com.lemondrop.service;

import com.lemondrop.model.Counter;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class CounterService {

    private final MongoOperations mongoOperations;

    public CounterService(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    public synchronized String getNextOrderCode(int year) {
        String counterId = "order_code_seq_" + year;
        
        Query query = new Query(Criteria.where("_id").is(counterId));
        Update update = new Update().inc("sequence", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
        
        Counter counter = mongoOperations.findAndModify(query, update, options, Counter.class);
        long sequence = (counter != null) ? counter.getSequence() : 1L;
        
        return String.format("LD-%d-%05d", year, sequence);
    }
}
