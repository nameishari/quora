package com.upgrad.quora.service.business;

import com.upgrad.quora.service.dao.QuestionDao;
import com.upgrad.quora.service.dao.UserDao;
import com.upgrad.quora.service.entity.QuestionEntity;
import com.upgrad.quora.service.entity.UserAuthTokenEntity;
import com.upgrad.quora.service.entity.UserEntity;
import com.upgrad.quora.service.error.ValidationErrors;
import com.upgrad.quora.service.exception.AuthorizationFailedException;
import com.upgrad.quora.service.exception.InvalidQuestionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.upgrad.quora.service.util.DateUtils.isBeforeNow;
import static java.util.Objects.nonNull;
import static java.util.Objects.isNull;

@Service
public class QuestionService {

    private static final String ADMIN = "admin";

    private final QuestionDao questionDao;
    private final UserDao userDao;

    @Autowired
    public QuestionService(QuestionDao questionDao, UserDao userDao) {
        this.questionDao = questionDao;
        this.userDao = userDao;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public QuestionEntity create(QuestionEntity question, String token) throws AuthorizationFailedException {
        UserAuthTokenEntity authToken = userDao.getAuthTokenByAccessToken(token);
        if (isNull(authToken)) {
            throw new AuthorizationFailedException(ValidationErrors.USER_NOT_SIGNED_IN.getCode(),
                    ValidationErrors.USER_NOT_SIGNED_IN.getReason());
        }
        if (isSignedOut(authToken)) {
            throw new AuthorizationFailedException(ValidationErrors.POST_A_QUESTION_SIGNED_OUT.getCode(),
                    ValidationErrors.POST_A_QUESTION_SIGNED_OUT.getReason());
        }
        question.setUser(authToken.getUser());
        return questionDao.createQuestion(question);
    }

    public List<QuestionEntity> getAll(String token) throws AuthorizationFailedException {
        UserAuthTokenEntity authToken = userDao.getAuthTokenByAccessToken(token);
        if (isNull(authToken)) {
            throw new AuthorizationFailedException(ValidationErrors.USER_NOT_SIGNED_IN.getCode(),
                    ValidationErrors.USER_NOT_SIGNED_IN.getReason());
        }
        if (isSignedOut(authToken)) {
            throw new AuthorizationFailedException(ValidationErrors.GET_ALL_QUESTIONS_SIGNED_OUT.getCode(),
                    ValidationErrors.GET_ALL_QUESTIONS_SIGNED_OUT.getReason());
        }
        return questionDao.findAll();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(String uuid, String token) throws AuthorizationFailedException, InvalidQuestionException {
        UserAuthTokenEntity authToken = userDao.getAuthTokenByAccessToken(token);
        if (isNull(authToken)) {
            throw new AuthorizationFailedException(ValidationErrors.USER_NOT_SIGNED_IN.getCode(),
                    ValidationErrors.USER_NOT_SIGNED_IN.getReason());
        }
        if (isSignedOut(authToken)) {
            throw new AuthorizationFailedException(ValidationErrors.DELETE_QUESTION_SIGNED_OUT.getCode(),
                    ValidationErrors.DELETE_QUESTION_SIGNED_OUT.getReason());
        }
        QuestionEntity question = questionDao.findQuestionByUUID(uuid)
                .orElseThrow(() -> new InvalidQuestionException(ValidationErrors.INVALID_QUESTION.getCode(),
                        ValidationErrors.INVALID_QUESTION.getReason()));
        if (isAdmin(authToken.getUser()) || isOwner(authToken.getUser(), question)) {
            questionDao.delete(question);
        } else {
            throw new AuthorizationFailedException(ValidationErrors.QUESTION_OWNER_ADMIN_ONLY_CAN_DELETE.getCode(),
                    ValidationErrors.QUESTION_OWNER_ADMIN_ONLY_CAN_DELETE.getReason());
        }
    }

    private boolean isSignedOut(UserAuthTokenEntity authToken) {
        return nonNull(authToken.getLogoutAt()) || isBeforeNow(authToken.getExpiresAt());
    }

    private boolean isAdmin(UserEntity user) {
        return ADMIN.equals(user.getRole());
    }

    private boolean isOwner(UserEntity userEntity, QuestionEntity questionEntity) {
        return userEntity.getUuid().equals(questionEntity.getUser().getUuid());
    }
}
