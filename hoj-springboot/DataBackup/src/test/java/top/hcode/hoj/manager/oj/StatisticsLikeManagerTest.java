package top.hcode.hoj.manager.oj;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import top.hcode.hoj.dao.discussion.CommentEntityService;
import top.hcode.hoj.dao.discussion.CommentLikeEntityService;
import top.hcode.hoj.dao.discussion.DiscussionEntityService;
import top.hcode.hoj.dao.discussion.DiscussionLikeEntityService;
import top.hcode.hoj.pojo.entity.discussion.Comment;
import top.hcode.hoj.pojo.entity.discussion.CommentLike;
import top.hcode.hoj.pojo.entity.discussion.Discussion;
import top.hcode.hoj.pojo.entity.discussion.DiscussionLike;
import top.hcode.hoj.shiro.AccountProfile;
import top.hcode.hoj.utils.RedisUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsLikeManagerTest {

    private Subject subject;
    private AccountProfile profile;

    @BeforeEach
    void bindSubject() {
        subject = mock(Subject.class);
        profile = new AccountProfile();
        profile.setUid("user-1");
        profile.setUsername("liker");
        when(subject.getPrincipal()).thenReturn(profile);
        ThreadContext.bind(subject);
    }

    @AfterEach
    void unbindSubject() {
        ThreadContext.unbindSubject();
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedDiscussionLikeDoesNotIncrementTheCounterAgain() throws Exception {
        DiscussionEntityService discussionService = mock(DiscussionEntityService.class);
        DiscussionLikeEntityService likeService = mock(DiscussionLikeEntityService.class);
        RedisUtils redisUtils = redisUtilsWithAvailableLock();
        DiscussionManager manager = new DiscussionManager();
        ReflectionTestUtils.setField(manager, "discussionEntityService", discussionService);
        ReflectionTestUtils.setField(manager, "discussionLikeEntityService", likeService);
        ReflectionTestUtils.setField(manager, "redisUtils", redisUtils);

        when(discussionService.getById(7)).thenReturn(new Discussion()
                .setId(7)
                .setUid("owner-1")
                .setAuthor("owner"));
        when(likeService.getOne(any(), eq(false))).thenReturn(new DiscussionLike()
                .setId(10)
                .setUid(profile.getUid())
                .setDid(7));

        manager.addDiscussionLike(7, true);

        verify(likeService, never()).save(any(DiscussionLike.class));
        verify(discussionService, never()).update(any(Wrapper.class));
        verify(discussionService, never()).updatePostLikeMsg(anyString(), anyString(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedCommentLikeDoesNotIncrementTheCounterAgain() throws Exception {
        CommentEntityService commentService = mock(CommentEntityService.class);
        CommentLikeEntityService likeService = mock(CommentLikeEntityService.class);
        RedisUtils redisUtils = redisUtilsWithAvailableLock();
        CommentManager manager = new CommentManager();
        ReflectionTestUtils.setField(manager, "commentEntityService", commentService);
        ReflectionTestUtils.setField(manager, "commentLikeEntityService", likeService);
        ReflectionTestUtils.setField(manager, "redisUtils", redisUtils);

        when(commentService.getById(9)).thenReturn(new Comment()
                .setId(9)
                .setFromUid("owner-1")
                .setFromName("owner"));
        when(likeService.getOne(any(), eq(false))).thenReturn(new CommentLike()
                .setId(11)
                .setUid(profile.getUid())
                .setCid(9));

        manager.addCommentLike(9, true, 7, "Discussion");

        verify(likeService, never()).save(any(CommentLike.class));
        verify(commentService, never()).update(any(Wrapper.class));
        verify(commentService, never()).updateCommentLikeMsg(anyString(), anyString(), any(), anyString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RedisUtils redisUtilsWithAvailableLock() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);
        RedisUtils redisUtils = new RedisUtils();
        redisUtils.setRedisTemplate((RedisTemplate) redisTemplate);
        return redisUtils;
    }
}
