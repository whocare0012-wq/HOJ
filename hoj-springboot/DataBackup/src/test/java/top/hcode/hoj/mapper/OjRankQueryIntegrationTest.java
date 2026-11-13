package top.hcode.hoj.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import top.hcode.hoj.pojo.vo.OIRankVO;
import top.hcode.hoj.agent.AgentJson;
import top.hcode.hoj.dao.user.impl.UserRecordEntityServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: a disposable local MySQL copy, never the website database. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OjRankQueryIntegrationTest {
    private DriverManagerDataSource admin;
    private JdbcTemplate jdbc;
    private SqlSessionFactory factory;
    private String schema;
    private String reference;

    private static DriverManagerDataSource dataSource(String url) {
        return new DriverManagerDataSource(url, "root", System.getenv("HOJ_RANK_QA_PASSWORD"));
    }

    private static SqlSessionFactory mapperFactory(DataSource ds) throws Exception {
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setLogImpl(NoLoggingImpl.class);
        config.setEnvironment(new Environment("local-rank-check", new JdbcTransactionFactory(), ds));
        // Exercise the real pagination plugin as well: it must not double-page the OI SQL.
        config.addInterceptor(new PaginationInterceptor());
        String resource = "top/hcode/hoj/mapper/xml/UserRecordMapper.xml";
        try (InputStream input = OjRankQueryIntegrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return new MybatisSqlSessionFactoryBuilder().build(config);
    }

    @BeforeAll
    void prepare() throws Exception {
        String url = System.getenv("HOJ_RANK_QA_JDBC_URL");
        Assumptions.assumeTrue(url != null, "Explicit local MySQL QA connection required");
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"), "Local Docker port only");
        admin = dataSource(url);
        assertEquals(System.getenv("HOJ_RANK_QA_SERVER_UUID"),
                new JdbcTemplate(admin).queryForObject("SELECT @@server_uuid", String.class));
        schema = "hoj_rank_verify_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        String fixtureUrl = url.replaceFirst("/hoj(?=\\?|$)", "/" + schema);
        assertNotEquals(url, fixtureUrl);
        DriverManagerDataSource ds = dataSource(fixtureUrl);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE user_info (uuid varchar(32) PRIMARY KEY, username varchar(100),"
                + "nickname varchar(100),signature text,avatar varchar(200),title_name varchar(100),"
                + "title_color varchar(20),status int)");
        jdbc.execute("CREATE TABLE problem (id bigint PRIMARY KEY,difficulty int,type int,io_score int)");
        jdbc.execute("CREATE TABLE problem_difficulty_config (difficulty_value int PRIMARY KEY,"
                + "display_text varchar(20),border_color varchar(7),sort_order int)");
        jdbc.execute("CREATE TABLE judge (submit_id bigint AUTO_INCREMENT PRIMARY KEY,uid varchar(32),"
                + "pid bigint,cid bigint,gid bigint,status int,score int,"
                + "gmt_modified datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
        Path repo = Paths.get("").toAbsolutePath();
        while (repo != null && !Files.exists(repo.resolve("sqlAndsetting/hoj-oj-points-update.sql"))) {
            repo = repo.getParent();
        }
        assertNotNull(repo);
        try (Connection connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(repo.resolve("sqlAndsetting/hoj-oj-points-update.sql").toFile()));
        }
        jdbc.update("INSERT INTO problem_difficulty_config VALUES (0,'Easy','#111111',0,10),"
                + "(1,'Medium','#222222',1,20),(2,'Hard','#333333',2,40),(3,'Zero','#444444',3,0)");
        jdbc.update("INSERT INTO problem VALUES (1,0,0,100),(2,1,1,100),(3,1,1,3),(4,3,0,100),(5,9,0,100)");
        try (InputStream input = getClass().getResourceAsStream("/oi-rank-reference.sql")) {
            assertNotNull(input);
            reference = new String(readAll(input), StandardCharsets.UTF_8).trim().replaceFirst(";$", "");
        }
        factory = mapperFactory(ds);
        seed();
    }

    private static byte[] readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];int n;
        while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private void user(String uid, int status) {
        jdbc.update("INSERT INTO user_info VALUES (?, ?, ?, 'signature',NULL,NULL,NULL,?)", uid, uid, uid, status);
    }

    private void submit(String uid, int pid, int status, Integer score, int scoreType,
                        int maximum, int cid, Long gid, int estimated) {
        jdbc.update("INSERT INTO judge (uid,pid,status,score,score_type,score_max,cid,gid,score_snapshot_estimated)"
                + " VALUES (?,?,?,?,?,?,?,?,?)", uid,pid,status,score,scoreType,maximum,cid,gid,estimated);
    }

    private void seed() {
        for (String uid : Arrays.asList("tie_a","tie_b","third","empty","zero_ac","missing_difficulty",
                "group_only","contest_only","negative","zero_maximum","estimated")) user(uid,0);
        for (String uid : Arrays.asList("tie_a","tie_b")) {
            submit(uid,1,0,null,0,0,0,null,0);
            submit(uid,1,0,100,0,100,0,null,0);
            submit(uid,2,8,60,1,100,0,null,0);
            submit(uid,2,8,30,1,100,0,null,0);
            submit(uid,3,8,1,1,3,0,null,0);
        }
        submit("tie_b",1,-1,100,0,100,0,null,0);
        submit("third",2,0,1,1,100,0,null,0);
        submit("zero_ac",4,0,100,0,100,0,null,0);
        submit("missing_difficulty",5,0,100,0,100,0,null,0);
        submit("group_only",1,0,100,0,100,0,42L,0);
        submit("contest_only",1,0,100,0,100,99,null,0);
        submit("negative",2,8,-5,1,100,0,null,0);
        submit("zero_maximum",2,8,100,1,0,0,null,0);
        submit("estimated",2,8,70,1,100,0,null,1);
        submit("estimated",2,8,50,1,100,0,null,0);
        for (int status : new int[]{-10,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7,8,9,10,15}) {
            String uid="status_"+status;user(uid,0);
            submit(uid,2,status,150,1,100,0,null,0);
        }
        user("disabled",1);submit("disabled",2,0,100,1,100,0,null,0);
    }

    private List<OIRankVO> expected(JdbcTemplate db) {
        return db.query(reference, (rs, row) -> {
            OIRankVO vo=new OIRankVO();vo.setUid(rs.getString("uid"));vo.setUsername(rs.getString("username"));
            vo.setNickname(rs.getString("nickname"));vo.setSignature(rs.getString("signature"));
            vo.setAvatar(rs.getString("avatar"));vo.setTitleName(rs.getString("title_name"));
            vo.setTitleColor(rs.getString("title_color"));vo.setScore(rs.getBigDecimal("score"));
            vo.setAc(rs.getInt("ac"));vo.setTotal(rs.getInt("total"));vo.setRankPosition(rs.getLong("rank_position"));
            return vo;
        });
    }

    private List<OIRankVO> actual(SqlSessionFactory mapper, long offset, long limit, List<String> uids) {
        try (SqlSession session=mapper.openSession()) {
            return session.getMapper(UserRecordMapper.class).getOIRankList(offset,limit,uids);
        }
    }

    private static void same(List<OIRankVO> expected, List<OIRankVO> actual) {
        assertEquals(expected.size(),actual.size());
        for(int i=0;i<expected.size();i++) {
            OIRankVO e=expected.get(i),a=actual.get(i);
            assertEquals(e.getUid(),a.getUid(),"User order");
            assertEquals(0,e.getScore().compareTo(a.getScore()),"Points");
            assertEquals(e.getAc(),a.getAc(),"Accepted count");assertEquals(e.getTotal(),a.getTotal(),"Submission count");
            assertEquals(e.getRankPosition(),a.getRankPosition(),"Global rank");
            assertTrue(Objects.equals(e.getUsername(),a.getUsername()) && Objects.equals(e.getNickname(),a.getNickname())
                    && Objects.equals(e.getSignature(),a.getSignature()) && Objects.equals(e.getAvatar(),a.getAvatar())
                    && Objects.equals(e.getTitleName(),a.getTitleName()) && Objects.equals(e.getTitleColor(),a.getTitleColor()),"Profile fields");
        }
    }

    @Test
    void canonicalPointsRulesAndTieRanksArePreserved() {
        List<OIRankVO> expected=expected(jdbc);
        same(expected,actual(factory,0,1000,null));
        Map<String,OIRankVO> byUid=expected.stream().collect(Collectors.toMap(OIRankVO::getUid,v->v));
        assertEquals(new BigDecimal("28.67"),byUid.get("tie_a").getScore());
        assertEquals(byUid.get("tie_a").getRankPosition(),byUid.get("tie_b").getRankPosition());
        assertNotEquals(byUid.get("tie_a").getTotal(),byUid.get("tie_b").getTotal());
        assertEquals(Integer.valueOf(1),byUid.get("zero_ac").getAc());
        assertEquals(0,byUid.get("group_only").getScore().signum());
        assertEquals(Integer.valueOf(0),byUid.get("contest_only").getTotal());
        assertFalse(byUid.containsKey("disabled"));
    }

    @Test
    void paginationAndSearchPreserveGlobalPositions() {
        List<OIRankVO> all=expected(jdbc);
        same(all.subList(5,10),actual(factory,5,5,null));
        List<String> uids=Arrays.asList("tie_b","third","empty");
        List<OIRankVO> selected=all.stream().filter(v->uids.contains(v.getUid())).collect(Collectors.toList());
        same(selected,actual(factory,0,100,uids));
        same(selected.subList(1,2),actual(factory,1,1,uids));
        assertTrue(actual(factory,0,20,Collections.emptyList()).isEmpty());
        assertTrue(actual(factory,999,20,null).isEmpty());
    }

    @Test
    void servicePreservesPageMetadataAndDoesNotDoublePaginate() throws Exception {
        try(SqlSession session=factory.openSession()) {
            UserRecordEntityServiceImpl service=new UserRecordEntityServiceImpl();
            java.lang.reflect.Field field=service.getClass().getDeclaredField("userRecordMapper");
            field.setAccessible(true);field.set(service,session.getMapper(UserRecordMapper.class));
            List<OIRankVO> all=expected(jdbc);
            Page<OIRankVO> page=new Page<>(2,5,all.size());
            assertSame(page,service.getOIRankList(page,null));
            assertEquals(2,page.getCurrent());assertEquals(all.size(),page.getTotal());
            same(all.subList(5,10),page.getRecords());
        }
    }

    @Test
    void changedDifficultyPointsAreVisibleImmediately() {
        try {
            jdbc.update("UPDATE problem SET difficulty=2 WHERE id=1");
            same(expected(jdbc),actual(factory,0,1000,null));
            jdbc.update("UPDATE problem_difficulty_config SET base_points=5 WHERE difficulty_value=2");
            same(expected(jdbc),actual(factory,0,1000,null));
        } finally {
            jdbc.update("UPDATE problem SET difficulty=0 WHERE id=1");
            jdbc.update("UPDATE problem_difficulty_config SET base_points=40 WHERE difficulty_value=2");
        }
    }

    @Test
    void restoredDatasetAndExecutionPlanMatch() throws Exception {
        JdbcTemplate copy=new JdbcTemplate(admin);
        Assumptions.assumeTrue(copy.queryForObject("SELECT COUNT(*) FROM judge",Long.class)>300000,
                "Restored production-sized copy required");
        SqlSessionFactory copiedFactory=mapperFactory(admin);
        List<OIRankVO> all=expected(copy);
        same(all,actual(copiedFactory,0,10000,null));
        same(all.subList(0,5),actual(copiedFactory,0,5,null));
        same(all.subList(30,60),actual(copiedFactory,30,30,null));
        List<String> uids=Arrays.asList(all.get(7).getUid(),all.get(31).getUid());
        same(all.stream().filter(v->uids.contains(v.getUid())).collect(Collectors.toList()),actual(copiedFactory,0,20,uids));
        List<Double> oldTimes=new ArrayList<>(),newTimes=new ArrayList<>();
        for(int i=0;i<5;i++) {
            long started=System.nanoTime();
            List<OIRankVO> before=copy.query(reference+" LIMIT 0,5",(rs,row)->{
                OIRankVO v=new OIRankVO();v.setUid(rs.getString("uid"));return v;
            });
            oldTimes.add((System.nanoTime()-started)/1_000_000_000.0);
            started=System.nanoTime();List<OIRankVO> after=actual(copiedFactory,0,5,null);
            newTimes.add((System.nanoTime()-started)/1_000_000_000.0);
            assertEquals(before.stream().map(OIRankVO::getUid).collect(Collectors.toList()),
                    after.stream().map(OIRankVO::getUid).collect(Collectors.toList()));
        }
        Map<String,Object> params=new HashMap<>();params.put("offset",0L);params.put("limit",5L);params.put("uidList",null);
        BoundSql bound=copiedFactory.getConfiguration().getMappedStatement(
                UserRecordMapper.class.getName()+".getOIRankList").getBoundSql(params);
        String optimized=bound.getSql().replaceFirst("\\?","0").replaceFirst("\\?","5");
        assertEquals(2,bound.getParameterMappings().size());
        String oldPlan=copy.queryForObject("EXPLAIN ANALYZE "+reference+" LIMIT 0,5",String.class);
        String newPlan=copy.queryForObject("EXPLAIN ANALYZE "+optimized,String.class);
        java.util.regex.Pattern loops=java.util.regex.Pattern.compile(
                "subquery in projection; dependent[\\s\\S]*?Aggregate: count\\([^\\n]*loops=(\\d+)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher oldLoops=loops.matcher(oldPlan),newLoops=loops.matcher(newPlan);
        assertTrue(oldLoops.find());assertTrue(newLoops.find());
        assertEquals(all.size(),Integer.parseInt(oldLoops.group(1)));
        assertEquals(5,Integer.parseInt(newLoops.group(1)));
        assertTrue(newPlan.contains("Covering index"));
        List<Double> sortedOld=new ArrayList<>(oldTimes),sortedNew=new ArrayList<>(newTimes);
        Collections.sort(sortedOld);Collections.sort(sortedNew);
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("mysqlVersion",copy.queryForObject("SELECT VERSION()",String.class));
        report.put("usersCompared",all.size());report.put("fullRankingIdentical",true);
        report.put("submissionRows",copy.queryForObject("SELECT COUNT(*) FROM judge",Long.class));
        report.put("oldSeconds",oldTimes);report.put("newSeconds",newTimes);
        report.put("oldMedianSeconds",sortedOld.get(2));report.put("newMedianSeconds",sortedNew.get(2));
        report.put("submissionCountLoopsBefore",Integer.parseInt(oldLoops.group(1)));
        report.put("submissionCountLoopsAfter",Integer.parseInt(newLoops.group(1)));
        Path output=Paths.get("target/rank-query-checks");Files.createDirectories(output);
        AgentJson.MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.resolve("benchmark.json").toFile(),report);
        Files.write(output.resolve("old-plan.txt"),oldPlan.getBytes(StandardCharsets.UTF_8));
        Files.write(output.resolve("new-plan.txt"),newPlan.getBytes(StandardCharsets.UTF_8));
        Files.write(output.resolve("optimized-top5.sql"),optimized.getBytes(StandardCharsets.UTF_8));
        assertTrue(sortedNew.get(2)<sortedOld.get(2),"Paired median should improve in the isolated copy");
    }

    @AfterAll
    void cleanup() {
        if(admin!=null && schema!=null) {
            assertTrue(schema.matches("hoj_rank_verify_[a-f0-9]{32}"));
            new JdbcTemplate(admin).execute("DROP DATABASE `"+schema+"`");
        }
    }
}
