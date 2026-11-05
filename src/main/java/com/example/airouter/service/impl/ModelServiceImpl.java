package com.example.airouter.service.impl;

import com.example.airouter.mapper.ModelMapper;
import com.example.airouter.model.entity.Model;
import com.example.airouter.service.ModelService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ModelServiceImpl extends ServiceImpl<ModelMapper, Model> implements ModelService {
}
