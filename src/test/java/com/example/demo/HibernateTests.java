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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
public class HibernateTests {
	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@Order(1)
	@Rollback(false)
	public void setup() {
		Parent p = new Parent("John");
		entityManager.persist(p);

		Child c = new Child("Alex", p);
		entityManager.persist(c);
	}

	@Test
	@Order(20)
	public void queryByObjectParameter_readFromDatabase() {
		resetSecondLevelCache();
		queryByObjectParameter();
	}

	@Test
	@Order(21)
	public void queryByObjectParameter_readFromL2Cache() {
		queryByObjectParameter();

		// This fails because querying by object results in a cache miss every time in Hibernate 6.1.7.
		// This succeeds in Hibernate 5.6.15.
		assertThat(getSecondLevelHitCount()).isEqualTo(1L);
	}

	private void queryByObjectParameter() {
		Logger logger = LoggerFactory.getLogger(this.getClass().getName() + ".queryByObjectParameter()");
		Session session = entityManager.unwrap(Session.class);

		Query<Parent> queryParent = session.createQuery("from Parent p where p.name = 'John'", Parent.class);
		List<Parent> p = queryParent.getResultList();
		assertThat(p).hasSize(1);

		Query<Child> queryChildren = session.createQuery("from Child c where c.parent = ?1", Child.class);
		queryChildren.setParameter(1, p.get(0));
		queryChildren.setCacheable(true);
		List<Child> c = queryChildren.getResultList();
		assertThat(c).hasSize(1);

		logger.debug("L2 cache hit count: {}", getSecondLevelHitCount());
	}

	@Test
	@Order(30)
	public void queryByIdParameter_readFromDatabase() {
		resetSecondLevelCache();
		queryByIdParameter();
	}

	@Test
	@Order(31)
	public void queryByIdParameter_readFromL2Cache() {
		queryByIdParameter();
		assertThat(getSecondLevelHitCount()).isEqualTo(1L); // querying by id correctly reads from the cache
	}

	private void queryByIdParameter() {
		Logger logger = LoggerFactory.getLogger(this.getClass().getName() + ".queryByIdParameter()");
		Session session = entityManager.unwrap(Session.class);

		Query<Parent> queryParent = session.createQuery("from Parent p where p.name = 'John'", Parent.class);
		List<Parent> p = queryParent.getResultList();
		assertThat(p).hasSize(1);

		Query<Child> queryChildren = session.createQuery("from Child c where c.parent.id = ?1", Child.class);
		queryChildren.setParameter(1, p.get(0).getId());
		queryChildren.setCacheable(true);
		List<Child> c = queryChildren.getResultList();
		assertThat(c).hasSize(1);

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
		CacheRegionStatistics regionStats = stats.getDomainDataRegionStatistics("default-query-results-region");
		return regionStats.getHitCount();
	}
}
