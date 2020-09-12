package akkount.service;

import io.jmix.core.JmixEntity;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;

@Service(UserDataService.NAME)
public class UserDataServiceBean implements UserDataService {

    @Inject
    protected UserDataWorker worker;

    @Override
    @Nullable
    public <T extends JmixEntity> T loadEntity(String key, Class<T> entityClass) {
        return worker.loadEntity(key, entityClass);
    }

    @Override
    public <T extends JmixEntity> List<T> loadEntityList(String key, Class<T> entityClass) {
        return worker.loadEntityList(key, entityClass);
    }

    @Override
    public void saveEntity(String key, JmixEntity entity) {
        worker.saveEntity(key, entity, false);
    }

    @Override
    public void addEntity(String key, JmixEntity entity) {
        worker.saveEntity(key, entity, true);
    }

    @Override
    public void removeEntity(String key, JmixEntity entity) {
        worker.removeEntity(key, entity);
    }
}