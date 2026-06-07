package com.labcourse.service.impl;

import com.labcourse.entity.Lab;
import com.labcourse.repository.LabRepository;
import com.labcourse.service.LabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@SuppressWarnings("null")
public class LabServiceImpl implements LabService {

    @Autowired
    private LabRepository labRepository;

    @Override
    public List<Lab> list() {
        return labRepository.findAll();
    }

    @Override
    public boolean save(Lab lab) {
        labRepository.save(lab);
        return true;
    }

    @Override
    public boolean updateById(Lab lab) {
        labRepository.save(lab);
        return true;
    }

    @Override
    public boolean removeById(Long id) {
        labRepository.deleteById(id);
        return true;
    }
}
