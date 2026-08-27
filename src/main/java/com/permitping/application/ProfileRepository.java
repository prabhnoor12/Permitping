package com.permitping.application;

import com.permitping.domain.Profile;
import java.util.List;

public interface ProfileRepository {
    List<Profile> findAll();
    default List<Profile> findArchived() { return List.of(); }
    void save(Profile profile);
    default void archive(long id) { }
    default void restore(long id) { }
    default void delete(long id) { }
}
