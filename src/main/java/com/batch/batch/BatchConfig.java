package com.batch.batch;
import com.batch.batch.entity.Person;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.*;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.net.BindException;
import java.time.LocalDate;

@Configuration
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public BatchConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public FlatFileItemReader<Person> reader() {
        return new FlatFileItemReaderBuilder<Person>()
                .name("csvReader")
                .resource(new ClassPathResource("utenti_sample.csv"))
                .delimited()
                .names("id", "nome", "cognome", "email", "data_nascita")
                .linesToSkip(1)
                .fieldSetMapper(fieldSet -> {
                    Person person = new Person();
                    person.setId(fieldSet.readLong("id"));
                    person.setFirstName(fieldSet.readString("nome"));
                    person.setLastName(fieldSet.readString("cognome"));
                    person.setEmail(fieldSet.readString("email"));
                    person.setBirthDate(LocalDate.parse(fieldSet.readString("data_nascita")));
                    return person;
                })
                .build();
    }

    @Bean
    public ItemProcessor<Person, Person> processor() {
        return person -> {
            person.setFirstName(person.getFirstName().toUpperCase());
            person.setLastName(person.getLastName().toUpperCase());
            return person;
        };
    }

    @Bean
    public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO persons (first_name,last_name,email,birth_date) VALUES (:firstName, :lastName,:email,:birthDate)")
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public Step csvStep(ItemReader<Person> reader,
                        ItemProcessor<Person, Person> processor,
                        ItemWriter<Person> writer) {
        return new StepBuilder("csvStep", jobRepository)
                .<Person, Person>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job csvJob(Step csvStep) {
        return new JobBuilder("csvJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(csvStep)
                .build();
    }
}