package study.querydsl.repository.custom;

import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.dto.QMemberTeamDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.MemberSearchCondition;

import java.util.List;

import static org.springframework.util.StringUtils.hasText;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepositoryCustom { //extends QuerydslRepositorySupport
    private final JPAQueryFactory queryFactory;

    //QuerydslRepositorySupport
//    public MemberRepositoryImpl(EntityManager em) {
//        super(Member.class);
//        this.queryFactory = new JPAQueryFactory(em);
//    }

    //회원명, 팀명, 나이(ageGoe, ageLoe)
    @Override
    public List<MemberTeamDto> search(MemberSearchCondition condition) {
        return queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .fetch();
    }

    @Override
    public Page<MemberTeamDto> searchPageSimple(MemberSearchCondition condition, Pageable pageable) {
        QueryResults<MemberTeamDto> results = queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .offset(pageable.getOffset()) //몇 번부터 시작할거야?
                .limit(pageable.getPageSize()) //1번 조회할 때 몇 개까지 가져올거야?
                .fetchResults(); // 쿼리 2번 나감(count 쿼리 + result 쿼리)

        List<MemberTeamDto> content = results.getResults(); //실제 데이터
        long total = results.getTotal();

        return new PageImpl<>(content, pageable, total);
    }

    //QuerydslRepositorySupport
 /*   @Override
    public Page<MemberTeamDto> searchPageSimple2(MemberSearchCondition condition, Pageable pageable) {
        JPQLQuery<MemberTeamDto> jpaQuery = from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")));

//        페이징을 Querydsl로 편리하게 변환 가능(sort 안됨!)
//        applyPagination = .offset(), .limit() 대신 해줌
        JPQLQuery<MemberTeamDto> query = getQuerydsl().applyPagination(pageable, jpaQuery);
        List<MemberTeamDto> fetch = query.fetch(); //query.fetchResults();

        List<MemberTeamDto> content = results.getResults(); //실제 데이터
        long total = results.getTotal();

        return new PageImpl<>(content, pageable, total);
    }*/


    /**
     * 복잡한 페이징
     * 데이터 조회 쿼리와, 전체 카운트 쿼리를 분리
     * - count 쿼리가 생략 가능한 경우 생략해서 처리
     * 1. 페이지 시작시 (content size) < (page size)인 경우
     * - ex) 전체데이터 = 100, size = 200 -> 2번째 페이지가 없음!
     * -> CountQuery 안 나감!
     * <p>
     * 2. 마지막 페이지 일 때 (offset + content size = 전체 사이즈)
     */
    @Override
    public Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable) {
        List<MemberTeamDto> content = queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch(); //return: content

        JPAQuery<Member> countQuery = queryFactory
                .select(member)
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                ); //countQuery

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchCount); //CountQuery 최적화
    }

    private BooleanExpression usernameEq(String username) {
        return hasText(username) ? member.username.eq(username) : null;
    }

    private BooleanExpression teamNameEq(String teamName) {
        return hasText(teamName) ? team.name.eq(teamName) : null;
    }

    private BooleanExpression ageGoe(Integer ageGoe) {
        return ageGoe != null ? member.age.goe(ageGoe) : null;
    }

    private BooleanExpression ageLoe(Integer ageLoe) {
        return ageLoe != null ? member.age.loe(ageLoe) : null;
    }
}