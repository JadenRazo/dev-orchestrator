package com.devorchestrator.mapper;

import com.devorchestrator.dto.EnvironmentDto;
import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.ContainerInstance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring", uses = {ContainerMapper.class})
public interface EnvironmentMapper {

    EnvironmentMapper INSTANCE = Mappers.getMapper(EnvironmentMapper.class);

    @Mapping(target = "templateId", source = "template.id")
    @Mapping(target = "templateName", source = "template.name")
    @Mapping(target = "userId", source = "owner.id")
    @Mapping(target = "username", source = "owner.username")
    @Mapping(target = "containers", source = "containers")
    @Mapping(target = "resourceUsage", expression = "java(buildResourceUsage(environment))")
    EnvironmentDto toDto(Environment environment);

    List<EnvironmentDto> toDtoList(List<Environment> environments);

    default EnvironmentDto.ResourceUsageDto buildResourceUsage(Environment environment) {
        if (environment.getTemplate() == null) {
            return null;
        }
        
        return EnvironmentDto.ResourceUsageDto.builder()
            .cpuLimit(environment.getTemplate().getCpuLimit().intValue())
            .memoryLimit(environment.getTemplate().getMemoryLimitMb().longValue())
            .cpuUsage(0.0) // TODO: Implement actual usage monitoring
            .memoryUsage(0L) // TODO: Implement actual usage monitoring
            .containerCount(environment.getContainers() != null ? environment.getContainers().size() : 0)
            .build();
    }
}