package com.example.demo;

import com.example.demo.model.Child;
import com.example.demo.model.Parent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
public class SimpleTests {
	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@Order(1)
	@Rollback(false)
	public void setup() {
		Parent p1 = new Parent("p1");
		entityManager.persist(p1);

		Child c1 = new Child("c1", p1);
		entityManager.persist(c1);

		Parent p2 = new Parent("p2");
		entityManager.persist(p2);

		Child c2 = new Child("c2", p2);
		entityManager.persist(c2);
	}

	@Test
	@Order(20)
	public void readObjectHql1() {
		resetSecondLevelCache();
		readObjectHql();
	}

	@Test
	@Order(21)
	public void readObjectHql2() {
		readObjectHql();
		assertThat(getSecondLevelHitCount()).isEqualTo(1L); // querying by object results in a cache miss every time
	}

	private void readObjectHql() {
		Logger logger = LoggerFactory.getLogger(this.getClass().getName() + ".readObjectHql()");
		Session session = entityManager.unwrap(Session.class);

		Query<Parent> query1 = session.createQuery("from Parent p where p.name = 'p1'", Parent.class);
		Parent p1 = query1.getResultList().get(0);
		assertThat(p1).isNotNull();

		Query<Child> query2 = session.createQuery("from Child c where c.parent = ?1", Child.class);
		query2.setParameter(1, p1);
		query2.setCacheable(true);
		Child c1 = query2.getResultList().get(0);
		assertThat(c1).isNotNull();

		logger.debug("L2 hit count {}", getSecondLevelHitCount());
	}

	@Test
	@Order(30)
	public void readHqlId1() {
		resetSecondLevelCache();
		readHqlId();
	}

	@Test
	@Order(31)
	public void readHqlId2() {
		readHqlId();
		assertThat(getSecondLevelHitCount()).isEqualTo(1L); // querying by id correctly reads from the cache
	}

	private void readHqlId() {
		Logger logger = LoggerFactory.getLogger(this.getClass().getName() + ".readHqlId()");
		Session session = entityManager.unwrap(Session.class);

		Query<Parent> query1 = session.createQuery("from Parent p where p.name = 'p1'", Parent.class);
		Parent p1 = query1.getResultList().get(0);
		assertThat(p1).isNotNull();

		Query<Child> query2 = session.createQuery("from Child c where c.parent.id = ?1", Child.class);
		query2.setParameter(1, p1.getId());
		query2.setCacheable(true);
		Child c1 = query2.getResultList().get(0);
		assertThat(c1).isNotNull();

		logger.debug("L2 hit count {}", getSecondLevelHitCount());
	}

	private void resetSecondLevelCache() {
		Session session = entityManager.unwrap(Session.class);
		session.getSessionFactory().getCache().evictQueryRegion("default-query-results-region");
		session.getSessionFactory().getStatistics().clear();

	}

	private long getSecondLevelHitCount() {
		Session session = entityManager.unwrap(Session.class);
		Statistics stats = session.getSessionFactory().getStatistics();
		CacheRegionStatistics stats2L = stats.getDomainDataRegionStatistics("default-query-results-region");
		return stats2L.getHitCount();
	}
}
