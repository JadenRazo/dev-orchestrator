package com.devorchestrator.mapper;

import com.devorchestrator.dto.EnvironmentTemplateDto;
import com.devorchestrator.entity.EnvironmentTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EnvironmentTemplateMapper {

    EnvironmentTemplateMapper INSTANCE = Mappers.getMapper(EnvironmentTemplateMapper.class);

    @Mapping(target = "category", constant = "development")  // TODO: Add category field to entity
    @Mapping(target = "version", constant = "1.0")  // TODO: Add version field to entity
    @Mapping(target = "isActive", source = "isPublic")
    @Mapping(target = "cpuLimit", source = "cpuLimit", qualifiedByName = "doubleToCpuLimit")
    @Mapping(target = "memoryLimit", source = "memoryLimitMb", qualifiedByName = "integerToMemoryLimit")
    @Mapping(target = "diskLimit", constant = "10240")  // TODO: Add disk limit to entity
    @Mapping(target = "configuration", expression = "java(buildConfiguration(template))")
    @Mapping(target = "services", expression = "java(null)")  // TODO: Parse docker compose
    @Mapping(target = "updatedAt", expression = "java(null)")  // TODO: Add updatedAt to entity
    EnvironmentTemplateDto toDto(EnvironmentTemplate template);

    @org.mapstruct.Named("doubleToCpuLimit")
    default Integer doubleToCpuLimit(Double cpuLimit) {
        return cpuLimit != null ? cpuLimit.intValue() : 1;
    }

    @org.mapstruct.Named("integerToMemoryLimit")
    default Long integerToMemoryLimit(Integer memoryLimitMb) {
        return memoryLimitMb != null ? memoryLimitMb.longValue() : 512L;
    }

    default java.util.Map<String, Object> buildConfiguration(EnvironmentTemplate template) {
        return java.util.Map.of(
            "dockerCompose", template.getDockerComposeContent() != null ? template.getDockerComposeContent() : "",
            "environmentVariables", template.getEnvironmentVariables() != null ? template.getEnvironmentVariables() : "",
            "exposedPorts", template.getExposedPorts() != null ? template.getExposedPorts() : java.util.Set.of()
        );
    }

    List<EnvironmentTemplateDto> toDtoList(List<EnvironmentTemplate> templates);
}