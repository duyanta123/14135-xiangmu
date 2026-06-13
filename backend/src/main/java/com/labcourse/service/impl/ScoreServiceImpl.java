package com.labcourse.service.impl;

import com.labcourse.entity.Score;
import com.labcourse.repository.ScoreRepository;
import com.labcourse.service.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@SuppressWarnings("null")
public class ScoreServiceImpl implements ScoreService {

    @Autowired
    private ScoreRepository scoreRepository;

    @Override
    @Transactional
    public boolean addScore(Long studentId, Long courseId, BigDecimal score) {
        Score existingScore = scoreRepository.findByStudentIdAndCourseId(studentId, courseId).orElse(null);

        if (existingScore != null) {
            existingScore.setScore(score);
            scoreRepository.save(existingScore);
        } else {
            Score newScore = new Score();
            newScore.setStudentId(studentId);
            newScore.setCourseId(courseId);
            newScore.setScore(score);
            scoreRepository.save(newScore);
        }
        return true;
    }

    @Override
    public List<Score> list() {
        return scoreRepository.findAll();
    }

    @Override
    public boolean save(Score score) {
        scoreRepository.save(score);
        return true;
    }

    @Override
    public boolean updateById(Score score) {
        scoreRepository.save(score);
        return true;
    }

    @Override
    public boolean removeById(Long id) {
        scoreRepository.deleteById(id);
        return true;
    }
}
