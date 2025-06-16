package com.devorchestrator.mapper;

import com.devorchestrator.dto.UserDto;
import com.devorchestrator.entity.User;
import com.devorchestrator.entity.EnvironmentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "isActive", constant = "true")  // TODO: Add isActive field to User entity
    @Mapping(target = "lastLoginAt", expression = "java(null)")  // TODO: Add lastLoginAt field to User entity
    @Mapping(target = "environmentCount", expression = "java(user.getEnvironments() != null ? user.getEnvironments().size() : 0)")
    @Mapping(target = "activeEnvironmentCount", expression = "java(countActiveEnvironments(user))")
    UserDto toDto(User user);

    List<UserDto> toDtoList(List<User> users);

    default Integer countActiveEnvironments(User user) {
        if (user.getEnvironments() == null) {
            return 0;
        }
        return (int) user.getEnvironments().stream()
            .filter(env -> env.getStatus() == EnvironmentStatus.RUNNING || 
                          env.getStatus() == EnvironmentStatus.CREATING ||
                          env.getStatus() == EnvironmentStatus.STARTING)
            .count();
    }
}