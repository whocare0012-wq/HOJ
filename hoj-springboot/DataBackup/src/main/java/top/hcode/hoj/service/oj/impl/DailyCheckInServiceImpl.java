package top.hcode.hoj.service.oj.impl;

import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.hcode.hoj.common.DailyFortuneType;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.mapper.DailyCheckInMapper;
import top.hcode.hoj.mapper.DailyFortuneAdviceMapper;
import top.hcode.hoj.mapper.DailyFortuneConfigMapper;
import top.hcode.hoj.pojo.vo.DailyCheckInVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdminOverviewVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdviceItemVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdvicePoolVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdviceRecordVO;
import top.hcode.hoj.pojo.vo.DailyFortuneConfigVO;
import top.hcode.hoj.service.oj.DailyCheckInService;
import top.hcode.hoj.shiro.AccountProfile;

import javax.annotation.Resource;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DailyCheckInServiceImpl implements DailyCheckInService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int ADVICE_PROFILE_COUNT = 40;
    private static final int CHECKED_IN_ADVICE_OFFSET = 11;
    private static final int DISPLAY_ADVICE_COUNT = 1;
    private static final int MIN_CONFIGURED_POOL_SIZE = 1;
    private static final int MAX_CONFIGURED_POOL_SIZE = 50;
    private static final String RECOMMENDED = "recommended";
    private static final String AVOID = "avoid";
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Map<DailyFortuneType, DailyFortuneAdvicePoolVO> DEFAULT_POOLS =
            createDefaultPools();

    @Resource
    private DailyCheckInMapper dailyCheckInMapper;

    @Resource
    private DailyFortuneAdviceMapper dailyFortuneAdviceMapper;

    @Resource
    private DailyFortuneConfigMapper dailyFortuneConfigMapper;

    @Override
    public CommonResult<DailyCheckInVO> getStatus() {
        AccountProfile profile = getCurrentProfile();
        LocalDate today = LocalDate.now(CHINA_ZONE);
        return CommonResult.successResponse(buildStatus(profile, today, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<DailyCheckInVO> checkIn() {
        AccountProfile profile = getCurrentProfile();
        LocalDate today = LocalDate.now(CHINA_ZONE);
        int inserted = dailyCheckInMapper.insertIgnore(profile.getUid(), Date.valueOf(today));
        DailyCheckInVO status = buildStatus(profile, today, inserted == 1);
        if (inserted == 1) {
            incrementAdviceUsage(status);
        }
        return CommonResult.successResponse(status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Map<String, DailyFortuneAdvicePoolVO>> getAdvicePools() {
        ensureDefaultAdvicePools();
        Map<String, DailyFortuneAdvicePoolVO> result = new LinkedHashMap<>();
        for (DailyFortuneConfigVO fortune : dailyFortuneConfigMapper.selectAll()) {
            result.put(fortune.getCode(), copyPool(loadAdvicePool(fortune, false)));
        }
        return CommonResult.successResponse(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<DailyFortuneAdvicePoolVO> updateAdvicePool(
            String fortuneType,
            DailyFortuneAdvicePoolVO advicePool) {
        DailyFortuneConfigVO fortune = dailyFortuneConfigMapper.selectByCode(fortuneType);
        if (fortune == null) {
            return CommonResult.errorResponse("不支持的运势类型！");
        }

        try {
            DailyFortuneAdvicePoolVO normalizedPool = normalizePool(advicePool);
            dailyFortuneAdviceMapper.deleteByFortuneType(fortune.getCode());
            insertAdviceItems(fortune.getCode(), RECOMMENDED, normalizedPool.getRecommended());
            insertAdviceItems(fortune.getCode(), AVOID, normalizedPool.getAvoid());
            return CommonResult.successResponse(copyPool(normalizedPool));
        } catch (IllegalArgumentException exception) {
            return CommonResult.errorResponse(exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<DailyFortuneAdminOverviewVO> getAdminOverview() {
        ensureDefaultAdvicePools();
        List<DailyFortuneConfigVO> fortunes = dailyFortuneConfigMapper.selectAll();
        for (DailyFortuneConfigVO fortune : fortunes) {
            fortune
                    .setRecommendedCount(dailyFortuneAdviceMapper
                            .countByFortuneTypeAndAdviceType(fortune.getCode(), RECOMMENDED))
                    .setAvoidCount(dailyFortuneAdviceMapper
                            .countByFortuneTypeAndAdviceType(fortune.getCode(), AVOID));
        }

        DailyFortuneAdminOverviewVO overview = new DailyFortuneAdminOverviewVO()
                .setFortuneCount(fortunes.size())
                .setRecommendedCount(dailyFortuneAdviceMapper.countByAdviceType(RECOMMENDED))
                .setAvoidCount(dailyFortuneAdviceMapper.countByAdviceType(AVOID))
                .setTodayCheckInCount(dailyCheckInMapper.countByDate(
                        Date.valueOf(LocalDate.now(CHINA_ZONE))))
                .setFortunes(fortunes);
        return CommonResult.successResponse(overview);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<DailyFortuneConfigVO> createFortune(DailyFortuneConfigVO fortune) {
        try {
            DailyFortuneConfigVO normalized = normalizeFortune(fortune, false);
            normalized
                    .setCode(generateFortuneCode())
                    .setSortOrder(fortune != null && fortune.getSortOrder() != null
                            ? fortune.getSortOrder()
                            : dailyFortuneConfigMapper.selectMaxSortOrder() + 1)
                    .setEnabled(false)
                    .setLegacyScore(64);
            dailyFortuneConfigMapper.insert(normalized);
            return CommonResult.successResponse(normalized);
        } catch (IllegalArgumentException exception) {
            return CommonResult.errorResponse(exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<DailyFortuneConfigVO> updateFortune(
            Long id,
            DailyFortuneConfigVO fortune) {
        DailyFortuneConfigVO existing = dailyFortuneConfigMapper.selectById(id);
        if (existing == null) {
            return CommonResult.errorResponse("运势不存在！");
        }
        try {
            DailyFortuneConfigVO normalized = normalizeFortune(fortune, true)
                    .setId(existing.getId())
                    .setCode(existing.getCode())
                    .setLegacyScore(existing.getLegacyScore());
            if (Boolean.TRUE.equals(normalized.getEnabled())) {
                validateEnabledFortunePool(existing.getCode());
            } else if (Boolean.TRUE.equals(existing.getEnabled())
                    && dailyFortuneConfigMapper.countEnabled() <= 1) {
                throw new IllegalArgumentException("至少需要保留一个启用的运势！");
            }
            dailyFortuneConfigMapper.update(normalized);
            return CommonResult.successResponse(normalized);
        } catch (IllegalArgumentException exception) {
            return CommonResult.errorResponse(exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> deleteFortune(Long id) {
        DailyFortuneConfigVO existing = dailyFortuneConfigMapper.selectById(id);
        if (existing == null) {
            return CommonResult.errorResponse("运势不存在！");
        }
        if (Boolean.TRUE.equals(existing.getEnabled())
                && dailyFortuneConfigMapper.countEnabled() <= 1) {
            return CommonResult.errorResponse("至少需要保留一个启用的运势！");
        }
        dailyFortuneAdviceMapper.deleteByFortuneType(existing.getCode());
        dailyFortuneConfigMapper.deleteById(id);
        return CommonResult.successResponse(null);
    }

    @Override
    public CommonResult<DailyFortuneAdvicePoolVO> getFortuneAdvice(String fortuneType) {
        DailyFortuneConfigVO fortune = dailyFortuneConfigMapper.selectByCode(fortuneType);
        if (fortune == null) {
            return CommonResult.errorResponse("运势不存在！");
        }
        return CommonResult.successResponse(copyPool(loadAdvicePool(fortune, false)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<DailyFortuneAdviceItemVO> createAdvice(
            String fortuneType,
            String adviceType,
            DailyFortuneAdviceItemVO item) {
        DailyFortuneConfigVO fortune = dailyFortuneConfigMapper.selectByCode(fortuneType);
        if (fortune == null) {
            return CommonResult.errorResponse("运势不存在！");
        }
        try {
            String normalizedType = normalizeAdviceType(adviceType);
            DailyFortuneAdviceItemVO normalized =
                    normalizeAdviceItem(item, fortuneType, normalizedType, null);
            if (item == null || item.getSortOrder() == null) {
                normalized.setSortOrder(
                        dailyFortuneAdviceMapper.selectMaxSortOrder(
                                fortuneType, normalizedType) + 1);
            }
            dailyFortuneAdviceMapper.insert(normalized);
            return CommonResult.successResponse(normalized);
        } catch (IllegalArgumentException exception) {
            return CommonResult.errorResponse(exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<DailyFortuneAdviceItemVO> updateAdvice(
            Long id,
            DailyFortuneAdviceItemVO item) {
        DailyFortuneAdviceRecordVO existing = dailyFortuneAdviceMapper.selectById(id);
        if (existing == null) {
            return CommonResult.errorResponse("词条不存在！");
        }
        try {
            DailyFortuneAdviceItemVO normalized = normalizeAdviceItem(
                    item,
                    existing.getFortuneType(),
                    existing.getAdviceType(),
                    id)
                    .setId(id)
                    .setUsageCount(existing.getUsageCount());
            validateAdviceAvailability(existing, normalized.getEnabled());
            dailyFortuneAdviceMapper.update(normalized);
            return CommonResult.successResponse(normalized);
        } catch (IllegalArgumentException exception) {
            return CommonResult.errorResponse(exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> deleteAdvice(Long id) {
        DailyFortuneAdviceRecordVO existing = dailyFortuneAdviceMapper.selectById(id);
        if (existing == null) {
            return CommonResult.errorResponse("词条不存在！");
        }
        try {
            validateAdviceAvailability(existing, false);
            dailyFortuneAdviceMapper.deleteById(id);
            return CommonResult.successResponse(null);
        } catch (IllegalArgumentException exception) {
            return CommonResult.errorResponse(exception.getMessage());
        }
    }

    private AccountProfile getCurrentProfile() {
        return (AccountProfile) SecurityUtils.getSubject().getPrincipal();
    }

    private DailyCheckInVO buildStatus(
            AccountProfile profile,
            LocalDate today,
            boolean newlyCheckedIn) {
        String uid = profile.getUid();
        Date checkInDate = Date.valueOf(today);
        boolean checkedIn = dailyCheckInMapper.countByUidAndDate(uid, checkInDate) > 0;
        DailyCheckInVO status = new DailyCheckInVO()
                .setCheckedIn(checkedIn)
                .setNewlyCheckedIn(newlyCheckedIn)
                .setCheckInDate(today.toString())
                .setTotalCheckInDays(dailyCheckInMapper.countByUid(uid))
                .setConsecutiveCheckInDays(consecutiveCheckInDays(uid, today, checkedIn))
                .setDisplayName(displayName(profile));

        // Reading today's status must not reveal or even generate fortune content.
        // The fortune is resolved only after a successful check-in record exists.
        if (!checkedIn) {
            return status
                    .setRecommendedItems(Collections.emptyList())
                    .setAvoidItems(Collections.emptyList());
        }

        DailyFortuneConfigVO fortune = fortuneType(uid, today);
        DailyFortuneAdvicePoolVO advicePool = loadAdvicePool(fortune, true);
        int focusScore = displayScore(score(uid, today, 17), checkedIn);
        int practiceScore = displayScore(score(uid, today, 37), checkedIn);
        int examScore = displayScore(score(uid, today, 73), checkedIn);
        int baseAdviceIndex = Math.floorMod(
                uid.hashCode() + (int) (today.toEpochDay() % ADVICE_PROFILE_COUNT),
                ADVICE_PROFILE_COUNT);
        int adviceIndex = checkedIn
                ? (baseAdviceIndex + CHECKED_IN_ADVICE_OFFSET) % ADVICE_PROFILE_COUNT
                : baseAdviceIndex;

        return status
                .setFortuneType(fortune.getCode())
                .setFortuneName(fortune.getName())
                .setFortuneColor(fortune.getColor())
                .setFortuneDescription(fortune.getDescription())
                .setRecommendedItems(selectAdviceItems(
                        advicePool.getRecommended(), uid, today, RECOMMENDED))
                .setAvoidItems(selectAdviceItems(
                        advicePool.getAvoid(), uid, today, AVOID))
                .setAdviceIndex(adviceIndex)
                .setFocusScore(focusScore)
                .setPracticeScore(practiceScore)
                .setExamScore(examScore)
                .setFortuneScore(fortune.getLegacyScore());
    }

    private String displayName(AccountProfile profile) {
        return StringUtils.hasText(profile.getNickname())
                ? profile.getNickname().trim()
                : profile.getUsername();
    }

    private DailyFortuneConfigVO fortuneType(String uid, LocalDate today) {
        List<DailyFortuneConfigVO> enabledFortunes = dailyFortuneConfigMapper.selectEnabled();
        if (enabledFortunes == null || enabledFortunes.isEmpty()) {
            return configFromType(DailyFortuneType.NEUTRAL);
        }
        int index = Math.floorMod(
                Objects.hash(uid, today.toEpochDay(), "daily-fortune"),
                enabledFortunes.size());
        return enabledFortunes.get(index);
    }

    private int consecutiveCheckInDays(String uid, LocalDate today, boolean checkedIn) {
        List<Date> checkInDates = dailyCheckInMapper.selectDatesByUid(uid);
        Set<LocalDate> dateSet = new HashSet<>();
        for (Date date : checkInDates) {
            dateSet.add(date.toLocalDate());
        }

        int days = 0;
        LocalDate cursor = checkedIn ? today : today.minusDays(1);
        while (dateSet.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        return days;
    }

    private DailyFortuneAdvicePoolVO loadAdvicePool(
            DailyFortuneConfigVO fortune,
            boolean enabledOnly) {
        List<DailyFortuneAdviceRecordVO> records =
                enabledOnly
                        ? dailyFortuneAdviceMapper.selectEnabledByFortuneType(fortune.getCode())
                        : dailyFortuneAdviceMapper.selectByFortuneType(fortune.getCode());
        if (records == null || records.isEmpty()) {
            DailyFortuneType defaultType = DailyFortuneType.fromCode(fortune.getCode());
            return defaultType == null
                    ? new DailyFortuneAdvicePoolVO()
                    : copyPool(DEFAULT_POOLS.get(defaultType));
        }

        DailyFortuneAdvicePoolVO configuredPool = new DailyFortuneAdvicePoolVO();
        for (DailyFortuneAdviceRecordVO record : records) {
            DailyFortuneAdviceItemVO item = adviceItemFromRecord(record);
            if (RECOMMENDED.equals(record.getAdviceType())) {
                configuredPool.getRecommended().add(item);
            } else if (AVOID.equals(record.getAdviceType())) {
                configuredPool.getAvoid().add(item);
            }
        }

        if (configuredPool.getRecommended().size() < MIN_CONFIGURED_POOL_SIZE
                || configuredPool.getAvoid().size() < MIN_CONFIGURED_POOL_SIZE) {
            DailyFortuneType defaultType = DailyFortuneType.fromCode(fortune.getCode());
            return defaultType == null
                    ? configuredPool
                    : copyPool(DEFAULT_POOLS.get(defaultType));
        }
        return configuredPool;
    }

    private List<DailyFortuneAdviceItemVO> selectAdviceItems(
            List<DailyFortuneAdviceItemVO> source,
            String uid,
            LocalDate today,
            String adviceType) {
        List<DailyFortuneAdviceItemVO> shuffled = copyItems(source);
        long seed = 31L * uid.hashCode()
                + 131L * today.toEpochDay()
                + adviceType.hashCode();
        Collections.shuffle(shuffled, new Random(seed));
        return new ArrayList<>(
                shuffled.subList(0, Math.min(DISPLAY_ADVICE_COUNT, shuffled.size())));
    }

    private DailyFortuneAdvicePoolVO normalizePool(DailyFortuneAdvicePoolVO advicePool) {
        if (advicePool == null) {
            throw new IllegalArgumentException("宜忌数据池不能为空！");
        }
        return new DailyFortuneAdvicePoolVO()
                .setRecommended(normalizeItems("宜", advicePool.getRecommended()))
                .setAvoid(normalizeItems("忌", advicePool.getAvoid()));
    }

    private List<DailyFortuneAdviceItemVO> normalizeItems(
            String label,
            List<DailyFortuneAdviceItemVO> items) {
        if (items == null || items.size() < MIN_CONFIGURED_POOL_SIZE) {
            throw new IllegalArgumentException(label + "的数据池至少需要2项！");
        }
        if (items.size() > MAX_CONFIGURED_POOL_SIZE) {
            throw new IllegalArgumentException(label + "的数据池最多只能设置50项！");
        }

        Set<String> uniqueTitles = new HashSet<>();
        List<DailyFortuneAdviceItemVO> normalizedItems = new ArrayList<>();
        for (DailyFortuneAdviceItemVO item : items) {
            String title = item == null || item.getTitle() == null
                    ? ""
                    : item.getTitle().trim();
            String description = item == null || item.getDescription() == null
                    ? ""
                    : item.getDescription().trim();
            if (!StringUtils.hasText(title)) {
                throw new IllegalArgumentException(label + "的事项标题不能为空！");
            }
            if (title.length() > 100 || description.length() > 255) {
                throw new IllegalArgumentException(label + "的事项内容超过长度限制！");
            }
            String normalizedTitle = title.toLowerCase(Locale.ROOT);
            if (!uniqueTitles.add(normalizedTitle)) {
                throw new IllegalArgumentException(label + "的数据池中不能有重复标题！");
            }
            normalizedItems.add(new DailyFortuneAdviceItemVO(title, description));
        }
        return normalizedItems;
    }

    private void insertAdviceItems(
            String fortuneType,
            String adviceType,
            List<DailyFortuneAdviceItemVO> items) {
        for (int index = 0; index < items.size(); index++) {
            DailyFortuneAdviceItemVO item = items.get(index);
            dailyFortuneAdviceMapper.insert(new DailyFortuneAdviceItemVO()
                    .setFortuneType(fortuneType)
                    .setAdviceType(adviceType)
                    .setTitle(item.getTitle())
                    .setDescription(item.getDescription())
                    .setSortOrder(index)
                    .setEnabled(true)
                    .setUsageCount(0L));
        }
    }

    private void ensureDefaultAdvicePools() {
        if (dailyFortuneAdviceMapper.countByAdviceType(RECOMMENDED)
                + dailyFortuneAdviceMapper.countByAdviceType(AVOID) > 0) {
            return;
        }
        for (DailyFortuneType type : DailyFortuneType.values()) {
            DailyFortuneAdvicePoolVO pool = DEFAULT_POOLS.get(type);
            insertAdviceItems(type.getCode(), RECOMMENDED, pool.getRecommended());
            insertAdviceItems(type.getCode(), AVOID, pool.getAvoid());
        }
    }

    private DailyFortuneConfigVO normalizeFortune(
            DailyFortuneConfigVO fortune,
            boolean editing) {
        if (fortune == null) {
            throw new IllegalArgumentException("运势数据不能为空！");
        }
        String name = fortune.getName() == null ? "" : fortune.getName().trim();
        String color = fortune.getColor() == null
                ? ""
                : fortune.getColor().trim().toUpperCase(Locale.ROOT);
        String description = fortune.getDescription() == null
                ? ""
                : fortune.getDescription().trim();
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("运势名称不能为空！");
        }
        if (name.length() > 32) {
            throw new IllegalArgumentException("运势名称不能超过32个字符！");
        }
        if (!COLOR_PATTERN.matcher(color).matches()) {
            throw new IllegalArgumentException("展示颜色必须是六位十六进制颜色！");
        }
        if (description.length() > 255) {
            throw new IllegalArgumentException("运势说明不能超过255个字符！");
        }
        return new DailyFortuneConfigVO()
                .setName(name)
                .setColor(color)
                .setDescription(description)
                .setSortOrder(fortune.getSortOrder() == null ? 0 : fortune.getSortOrder())
                .setEnabled(editing && Boolean.TRUE.equals(fortune.getEnabled()));
    }

    private DailyFortuneAdviceItemVO normalizeAdviceItem(
            DailyFortuneAdviceItemVO item,
            String fortuneType,
            String adviceType,
            Long excludeId) {
        if (item == null) {
            throw new IllegalArgumentException("词条数据不能为空！");
        }
        String title = item.getTitle() == null ? "" : item.getTitle().trim();
        String description = item.getDescription() == null
                ? ""
                : item.getDescription().trim();
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("词条内容不能为空！");
        }
        if (title.length() > 100) {
            throw new IllegalArgumentException("词条内容不能超过100个字符！");
        }
        if (description.length() > 255) {
            throw new IllegalArgumentException("词条说明不能超过255个字符！");
        }
        if (dailyFortuneAdviceMapper.countDuplicateTitle(
                fortuneType, adviceType, title, excludeId) > 0) {
            throw new IllegalArgumentException("当前词条列表中已存在相同内容！");
        }
        return new DailyFortuneAdviceItemVO()
                .setFortuneType(fortuneType)
                .setAdviceType(adviceType)
                .setTitle(title)
                .setDescription(description)
                .setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder())
                .setEnabled(item.getEnabled() == null || item.getEnabled())
                .setUsageCount(item.getUsageCount() == null ? 0L : item.getUsageCount());
    }

    private String normalizeAdviceType(String adviceType) {
        if (RECOMMENDED.equals(adviceType) || AVOID.equals(adviceType)) {
            return adviceType;
        }
        throw new IllegalArgumentException("不支持的词条类型！");
    }

    private void validateEnabledFortunePool(String fortuneType) {
        if (dailyFortuneAdviceMapper.countEnabledByFortuneTypeAndAdviceType(
                fortuneType, RECOMMENDED) < MIN_CONFIGURED_POOL_SIZE
                || dailyFortuneAdviceMapper.countEnabledByFortuneTypeAndAdviceType(
                fortuneType, AVOID) < MIN_CONFIGURED_POOL_SIZE) {
            throw new IllegalArgumentException("启用运势前，宜和忌至少各需要一个启用词条！");
        }
    }

    private void validateAdviceAvailability(
            DailyFortuneAdviceRecordVO existing,
            boolean remainsEnabled) {
        if (!Boolean.TRUE.equals(existing.getEnabled()) || remainsEnabled) {
            return;
        }
        DailyFortuneConfigVO fortune =
                dailyFortuneConfigMapper.selectByCode(existing.getFortuneType());
        if (fortune != null
                && Boolean.TRUE.equals(fortune.getEnabled())
                && dailyFortuneAdviceMapper.countEnabledByFortuneTypeAndAdviceType(
                existing.getFortuneType(), existing.getAdviceType())
                <= MIN_CONFIGURED_POOL_SIZE) {
            throw new IllegalArgumentException("启用的运势中，宜和忌至少各保留一个启用词条！");
        }
    }

    private void incrementAdviceUsage(DailyCheckInVO status) {
        List<Long> ids = new ArrayList<>();
        appendAdviceIds(ids, status.getRecommendedItems());
        appendAdviceIds(ids, status.getAvoidItems());
        if (!ids.isEmpty()) {
            dailyFortuneAdviceMapper.incrementUsage(ids);
        }
    }

    private void appendAdviceIds(
            List<Long> ids,
            List<DailyFortuneAdviceItemVO> items) {
        if (items == null) {
            return;
        }
        for (DailyFortuneAdviceItemVO item : items) {
            if (item != null && item.getId() != null) {
                ids.add(item.getId());
            }
        }
    }

    private String generateFortuneCode() {
        String code;
        do {
            code = "fortune_" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12);
        } while (dailyFortuneConfigMapper.selectByCode(code) != null);
        return code;
    }

    private DailyFortuneAdviceItemVO adviceItemFromRecord(
            DailyFortuneAdviceRecordVO record) {
        return new DailyFortuneAdviceItemVO()
                .setId(record.getId())
                .setFortuneType(record.getFortuneType())
                .setAdviceType(record.getAdviceType())
                .setTitle(record.getTitle())
                .setDescription(record.getDescription())
                .setSortOrder(record.getSortOrder())
                .setEnabled(record.getEnabled())
                .setUsageCount(record.getUsageCount());
    }

    private DailyFortuneConfigVO configFromType(DailyFortuneType type) {
        return new DailyFortuneConfigVO()
                .setCode(type.getCode())
                .setName(type.getDisplayName())
                .setColor(type.getColor())
                .setDescription(type.getDescription())
                .setSortOrder(type.getSortOrder())
                .setEnabled(true)
                .setLegacyScore(type.getLegacyScore());
    }

    private int score(String uid, LocalDate today, int salt) {
        int hash = (uid + "#" + today + "#" + salt).hashCode() & Integer.MAX_VALUE;
        return 80 + hash % 20;
    }

    /**
     * Retained for old frontends that still render the three score bars.
     */
    private int displayScore(int baseScore, boolean checkedIn) {
        if (!checkedIn) {
            return baseScore;
        }
        return baseScore < 90 ? baseScore + 10 : baseScore - 10;
    }

    private static DailyFortuneAdvicePoolVO copyPool(DailyFortuneAdvicePoolVO source) {
        return new DailyFortuneAdvicePoolVO()
                .setRecommended(copyItems(source.getRecommended()))
                .setAvoid(copyItems(source.getAvoid()));
    }

    private static List<DailyFortuneAdviceItemVO> copyItems(
            List<DailyFortuneAdviceItemVO> source) {
        List<DailyFortuneAdviceItemVO> result = new ArrayList<>();
        for (DailyFortuneAdviceItemVO item : source) {
            result.add(new DailyFortuneAdviceItemVO()
                    .setId(item.getId())
                    .setFortuneType(item.getFortuneType())
                    .setAdviceType(item.getAdviceType())
                    .setTitle(item.getTitle())
                    .setDescription(item.getDescription())
                    .setSortOrder(item.getSortOrder())
                    .setEnabled(item.getEnabled())
                    .setUsageCount(item.getUsageCount()));
        }
        return result;
    }

    private static Map<DailyFortuneType, DailyFortuneAdvicePoolVO> createDefaultPools() {
        Map<DailyFortuneType, DailyFortuneAdvicePoolVO> pools = new LinkedHashMap<>();
        pools.put(DailyFortuneType.GREAT_LUCK, pool(
                items(
                        item("挑战一道压轴题", "状态正佳，适合攻克计划中的难题"),
                        item("参加一次模拟赛", "完整计时并在结束后马上复盘"),
                        item("整理核心算法模板", "补齐边界条件和使用说明"),
                        item("给同学讲一道题", "把关键思路和复杂度讲清楚")),
                items(
                        item("提交前省略自测", "好运也经不起未运行的边界样例"),
                        item("同时开启太多任务", "先把最重要的一件事做完"),
                        item("凭感觉猜复杂度", "动笔写出时间与空间复杂度"),
                        item("熬夜刷题", "好状态需要充足睡眠来维持"))));
        pools.put(DailyFortuneType.MEDIUM_LUCK, pool(
                items(
                        item("完成计划中的较难题", "先拆分步骤再逐一实现"),
                        item("复盘近期一次错误提交", "写清错误原因和修复方法"),
                        item("补全一组测试用例", "覆盖最小值、最大值和特殊情况"),
                        item("整理今天的学习计划", "把任务按优先级排好顺序")),
                items(
                        item("频繁切换题目", "给当前题目留出连续思考时间"),
                        item("忽略样例解释", "先确认输入输出的真实含义"),
                        item("复制未理解的代码", "每一段代码都要能自己说明"),
                        item("拖延错题复盘", "趁记忆清晰时马上记录"))));
        pools.put(DailyFortuneType.SMALL_LUCK, pool(
                items(
                        item("先做一道热身题", "用熟悉题型进入学习状态"),
                        item("复习一个薄弱知识点", "配合一道针对性练习"),
                        item("写下完整解题步骤", "编码前明确状态和转移过程"),
                        item("检查代码命名", "让变量和函数表达真实含义")),
                items(
                        item("一开始就死磕难题", "先建立节奏再提高难度"),
                        item("没看数据范围就编码", "范围决定算法和数据类型"),
                        item("只看答案不动手", "关掉题解后独立写一遍"),
                        item("提交后不看结果", "及时检查错误信息和用时"))));
        pools.put(DailyFortuneType.NEUTRAL, pool(
                items(
                        item("按计划完成今日任务", "稳步推进比临时加量更有效"),
                        item("整理一条错题记录", "记录现象、原因和正确做法"),
                        item("专注学习二十五分钟", "完成后再短暂休息"),
                        item("做一次代码自测", "提交前手动走一遍关键分支")),
                items(
                        item("临时增加太多目标", "保持今天的任务范围稳定"),
                        item("边学习边刷消息", "关闭通知直到本轮专注结束"),
                        item("跳过基础步骤", "先确认题意和数据范围"),
                        item("情绪化反复提交", "每次提交前都要有明确修改"))));
        pools.put(DailyFortuneType.BAD_LUCK, pool(
                items(
                        item("选择熟悉题型练手", "先用确定性任务建立节奏"),
                        item("先读完题目再编码", "标记约束、边界和特殊情况"),
                        item("逐行检查关键分支", "重点核对下标和循环边界"),
                        item("缩小今日任务范围", "优先保证一项任务完整完成")),
                items(
                        item("尝试高风险大改", "先保存可运行版本再逐步修改"),
                        item("连续盲目提交", "根据错误信息定位后再提交"),
                        item("忽略编译警告", "先处理类型和未初始化问题"),
                        item("疲劳时继续硬撑", "短暂休息后再做判断"))));
        pools.put(DailyFortuneType.GREAT_BAD_LUCK, pool(
                items(
                        item("复习基础语法", "选择能独立完成的小练习"),
                        item("修复一个已知错误", "只处理范围明确的问题"),
                        item("备份重要代码", "确认本地和远端都有可用版本"),
                        item("早点结束学习休息", "为明天恢复稳定状态")),
                items(
                        item("参加未准备的模拟赛", "先补齐必要知识和模板"),
                        item("重构整套代码", "今天只做小范围可回退的修改"),
                        item("在生产环境直接试验", "先在本地或测试环境验证"),
                        item("熬夜追赶进度", "睡眠不足会放大低级错误"))));
        return Collections.unmodifiableMap(pools);
    }

    private static DailyFortuneAdvicePoolVO pool(
            List<DailyFortuneAdviceItemVO> recommended,
            List<DailyFortuneAdviceItemVO> avoid) {
        return new DailyFortuneAdvicePoolVO()
                .setRecommended(recommended)
                .setAvoid(avoid);
    }

    private static List<DailyFortuneAdviceItemVO> items(
            DailyFortuneAdviceItemVO... items) {
        List<DailyFortuneAdviceItemVO> result = new ArrayList<>();
        Collections.addAll(result, items);
        return result;
    }

    private static DailyFortuneAdviceItemVO item(String title, String description) {
        return new DailyFortuneAdviceItemVO(title, description);
    }
}
