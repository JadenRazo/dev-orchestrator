package com.devorchestrator.mapper;

import com.devorchestrator.dto.EnvironmentDto;
import com.devorchestrator.entity.ContainerInstance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ContainerMapper {

    ContainerMapper INSTANCE = Mappers.getMapper(ContainerMapper.class);

    @Mapping(target = "imageName", expression = "java(\"nginx:alpine\")")  // TODO: Extract from template
    @Mapping(target = "exposedPort", source = "hostPort")
    @Mapping(target = "containerId", source = "dockerContainerId")
    @Mapping(target = "status", expression = "java(container.getStatus().name())")
    EnvironmentDto.ContainerDto toDto(ContainerInstance container);

    List<EnvironmentDto.ContainerDto> toDtoList(List<ContainerInstance> containers);
}