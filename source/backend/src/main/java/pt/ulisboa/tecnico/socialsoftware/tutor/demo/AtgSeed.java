package pt.ulisboa.tecnico.socialsoftware.tutor.demo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pt.ulisboa.tecnico.socialsoftware.tutor.answer.AnswerService;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.domain.MultipleChoiceAnswer;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.domain.QuestionAnswer;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.domain.QuizAnswer;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.repository.QuizAnswerRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.auth.domain.AuthUser;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.domain.Discussion;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.domain.Reply;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.dto.DiscussionDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.dto.ReplyDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.repository.DiscussionRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.execution.CourseExecutionService;
import pt.ulisboa.tecnico.socialsoftware.tutor.execution.domain.Assessment;
import pt.ulisboa.tecnico.socialsoftware.tutor.execution.domain.CourseExecution;
import pt.ulisboa.tecnico.socialsoftware.tutor.execution.domain.TopicConjunction;
import pt.ulisboa.tecnico.socialsoftware.tutor.execution.repository.AssessmentRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Course;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Languages;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.MultipleChoiceQuestion;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Option;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Question;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Topic;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.dto.CodeFillInQuestionDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.dto.CodeFillInSpotDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.dto.CodeOrderQuestionDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.dto.CodeOrderSlotDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.dto.MultipleChoiceQuestionDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.dto.OptionDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.dto.QuestionDetailsDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.dto.QuestionDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.repository.QuestionRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.repository.TopicRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.questionsubmission.domain.QuestionSubmission;
import pt.ulisboa.tecnico.socialsoftware.tutor.questionsubmission.repository.QuestionSubmissionRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.quiz.domain.Quiz;
import pt.ulisboa.tecnico.socialsoftware.tutor.quiz.domain.QuizQuestion;
import pt.ulisboa.tecnico.socialsoftware.tutor.quiz.repository.QuizRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.tournament.domain.Tournament;
import pt.ulisboa.tecnico.socialsoftware.tutor.tournament.repository.TournamentRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.UserService;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.domain.Student;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.domain.Teacher;
import pt.ulisboa.tecnico.socialsoftware.tutor.utils.DateHandler;

/**
 * Data for the atg benchmark, only with --atg.seed=true. Use it with --demo.reset-on-startup=false:
 * the seed makes all the content of the demo course, so every start has the same data.
 *
 * Quizzes, tournaments and results depend on dates, so a test cannot reach every state through
 * the UI. The seed makes quizzes and tournaments in each state, with dates relative to the start.
 * Rows whose state is in the past are built with setters, because the constructors check the
 * dates at the time of the call.
 *
 * It runs in afterPropertiesSet, before the web server starts, so the API never answers before
 * the seed is done.
 */
@Component
@ConditionalOnProperty(name = "atg.seed", havingValue = "true")
public class AtgSeed implements InitializingBean {
    public static final int IN_CLASS_QUIZ_CODE = 424242;

    private final TransactionTemplate transaction;
    private final CourseExecutionService courseExecutionService;
    private final UserService userService;
    private final AnswerService answerService;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final AssessmentRepository assessmentRepository;
    private final QuizRepository quizRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final TournamentRepository tournamentRepository;
    private final DiscussionRepository discussionRepository;
    private final QuestionSubmissionRepository questionSubmissionRepository;

    public AtgSeed(PlatformTransactionManager transactionManager, CourseExecutionService courseExecutionService,
                   UserService userService, AnswerService answerService, TopicRepository topicRepository,
                   QuestionRepository questionRepository, AssessmentRepository assessmentRepository,
                   QuizRepository quizRepository, QuizAnswerRepository quizAnswerRepository,
                   TournamentRepository tournamentRepository, DiscussionRepository discussionRepository,
                   QuestionSubmissionRepository questionSubmissionRepository) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.courseExecutionService = courseExecutionService;
        this.userService = userService;
        this.answerService = answerService;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.assessmentRepository = assessmentRepository;
        this.quizRepository = quizRepository;
        this.quizAnswerRepository = quizAnswerRepository;
        this.tournamentRepository = tournamentRepository;
        this.discussionRepository = discussionRepository;
        this.questionSubmissionRepository = questionSubmissionRepository;
    }

    @Override
    public void afterPropertiesSet() {
        transaction.executeWithoutResult(status -> seed(DateHandler.now().withNano(0)));
        // The statistics of the questions (answers, correct answers) count the answers of the seed.
        answerService.writeQuizAnswersAndQuestionStatistics();
    }

    private void seed(LocalDateTime now) {
        CourseExecution execution = courseExecutionService.getDemoCourseExecution();
        Course course = execution.getCourse();

        // Users. The demo teacher and student are the ones the "Demo" login buttons use.
        Teacher teacher = (Teacher) userService.createTeacherWithAuth("Demo Teacher", DemoUtils.TEACHER_USERNAME,
                "demo_teacher@mail.com", AuthUser.Type.DEMO).getUser();
        teacher.addCourse(execution);
        Student student = student("Demo Student", DemoUtils.STUDENT_USERNAME, "demo_student@mail.com", execution);
        Student ana = student("Ana Silva", "ana", "ana@mail.com", execution);
        Student bruno = student("Bruno Costa", "bruno", "bruno@mail.com", execution);

        // Topics
        Topic architecture = topic(course, "Software Architecture");
        Topic engineering = topic(course, "Software Engineering");
        Topic testing = topic(course, "Software Testing");

        // Questions of the three types
        Question layers = multipleChoice(course, now, "Layered Architecture",
                "Which layer of a layered architecture talks to the database?", engineering, architecture,
                0, "Persistence layer", "Presentation layer", "Business layer", "Browser");
        Question microservices = multipleChoice(course, now, "Microservices",
                "What does each microservice own in a microservice architecture?", architecture, null,
                1, "A shared database with the other services", "Its own data", "The user interface of the system", "Nothing");
        Question coupling = multipleChoice(course, now, "Coupling",
                "Which design has lower coupling?", engineering, null,
                2, "Classes that read the fields of each other", "A class that calls static methods of many classes",
                "Classes that talk through small interfaces", "One class that does everything");
        Question patterns = multipleChoice(course, now, "Observer Pattern",
                "What does the Observer pattern let an object do?", engineering, null,
                3, "Create objects without naming their class", "Copy itself", "Hide its fields",
                "Tell other objects when its state changes");
        Question unitTests = multipleChoice(course, now, "Unit Tests",
                "What does a unit test check?", testing, null,
                0, "One unit of code, apart from the rest of the system", "The whole system through its interface",
                "The speed of the system", "The style of the code");
        Question mocks = multipleChoice(course, now, "Test Doubles",
                "Why does a test use a mock?", testing, null,
                1, "To make the test slower", "To replace a collaborator that the test does not check",
                "To test the database", "To skip the test");
        Question fillIn = codeFillIn(course, now, testing);
        Question order = codeOrder(course, now, engineering);

        // An assessment, which the student uses to create a quiz of random questions.
        Assessment assessment = new Assessment();
        assessment.setTitle("Software Engineering Questions");
        assessment.setStatus(Assessment.Status.AVAILABLE);
        assessment.setSequence(1);
        assessment.setCourseExecution(execution);
        TopicConjunction conjunction = new TopicConjunction();
        conjunction.addTopic(engineering);
        conjunction.addTopic(testing);
        conjunction.setAssessment(assessment);
        assessmentRepository.save(assessment);

        // Available: the student can solve it. It has a question of each type.
        quiz("Weekly Quiz", Quiz.QuizType.PROPOSED, execution, now.minusDays(1), null, null,
                List.of(layers, coupling, fillIn, order));

        // Answered by the demo student: solved quizzes, results, discussions and the dashboard.
        Quiz answered = quiz("Architecture Review", Quiz.QuizType.PROPOSED, execution, now.minusDays(10),
                now.minusDays(3), null, List.of(layers, microservices, patterns));
        QuizAnswer answers = answer(answered, student, now.minusDays(8), 0, 0, 1);
        answer(answered, ana, now.minusDays(9), 0, 1, 3);

        // In class, open with a code, with a long window so a slow test still finds it open.
        Quiz inClass = quiz("In Class Quiz", Quiz.QuizType.IN_CLASS, execution, now.minusHours(1),
                now.plusDays(2), null, List.of(unitTests, mocks));
        inClass.setCode(IN_CLASS_QUIZ_CODE);

        // Not available yet.
        quiz("Next Week Quiz", Quiz.QuizType.PROPOSED, execution, now.plusDays(7), null, null,
                List.of(unitTests, coupling));

        // Tournaments: open, upcoming and closed.
        Tournament open = tournament(ana, execution, Set.of(engineering, testing), now.minusHours(1), now.plusDays(3), 2);
        open.addParticipant(ana, "");
        Tournament upcoming = tournament(bruno, execution, Set.of(architecture), now.plusDays(2), now.plusDays(5), 2);
        upcoming.addParticipant(bruno, "");
        Tournament closed = tournament(ana, execution, Set.of(engineering), now.plusMinutes(1), now.plusDays(1), 2);
        closed.addParticipant(ana, "");
        closed.addParticipant(student, "");
        closed.setStartTime(now.minusDays(5));
        closed.setEndTime(now.minusDays(4));

        // A discussion of the demo student on the question they answered wrong, with a reply.
        QuestionAnswer wrong = answers.getQuestionAnswers().stream()
                .filter(questionAnswer -> questionAnswer.getQuizQuestion().getQuestion() == patterns)
                .findFirst().orElseThrow();
        DiscussionDto discussionDto = new DiscussionDto();
        discussionDto.setMessage("Why is my answer wrong? I thought the Observer pattern copies the object.");
        discussionDto.setDate(DateHandler.toISOString(now.minusDays(7)));
        Discussion discussion = new Discussion(wrong, discussionDto);
        ReplyDto replyDto = new ReplyDto();
        replyDto.setMessage("That is the Prototype pattern. The Observer pattern notifies other objects.");
        replyDto.setDate(DateHandler.toISOString(now.minusDays(6)));
        replyDto.setPublic(true);
        new Reply(teacher, replyDto, discussion);
        discussionRepository.save(discussion);

        // Questions that students submitted, waiting for the review of the teacher.
        submission(course, execution, ana, now.minusDays(2), "Refactoring",
                "What does a refactoring change?", engineering,
                "The structure of the code, not its behaviour", "The behaviour of the code", "The tests", "The requirements");
        submission(course, execution, student, now.minusDays(1), "Code Smells",
                "Which one is a code smell?", engineering,
                "A very long method", "A short method", "A unit test", "A comment that explains why");
    }

    private Student student(String name, String username, String email, CourseExecution execution) {
        Student student = (Student) userService.createStudentWithAuth(name, username, email, AuthUser.Type.DEMO).getUser();
        student.addCourse(execution);
        return student;
    }

    private Topic topic(Course course, String name) {
        Topic topic = new Topic();
        topic.setName(name);
        topic.setCourse(course);
        return topicRepository.save(topic);
    }

    private Question multipleChoice(Course course, LocalDateTime now, String title, String content, Topic topic,
                                    Topic otherTopic, int correct, String... options) {
        MultipleChoiceQuestionDto details = new MultipleChoiceQuestionDto();
        List<OptionDto> optionDtos = new ArrayList<>();
        for (int index = 0; index < options.length; index++) {
            OptionDto option = new OptionDto();
            option.setContent(options[index]);
            option.setCorrect(index == correct);
            optionDtos.add(option);
        }
        details.setOptions(optionDtos);
        return question(course, now, title, content, details, Question.Status.AVAILABLE, topic, otherTopic);
    }

    private Question codeFillIn(Course course, LocalDateTime now, Topic topic) {
        CodeFillInQuestionDto details = new CodeFillInQuestionDto();
        details.setLanguage(Languages.Java);
        details.setCode("""
                @Test
                void addsTwoNumbers() {
                    Calculator calculator = new Calculator();
                    {{slot-1}}(5, calculator.add(2, 3));
                }""");
        CodeFillInSpotDto spot = new CodeFillInSpotDto();
        spot.setSequence(1);
        spot.setOptions(new ArrayList<>(List.of(fillInOption("assertEquals", true, 1), fillInOption("assertNull", false, 2),
                fillInOption("assertThrows", false, 3))));
        details.setFillInSpots(new ArrayList<>(List.of(spot)));
        return question(course, now, "Assertions", "Complete the test so that it checks the sum.", details,
                Question.Status.AVAILABLE, topic, null);
    }

    private OptionDto fillInOption(String content, boolean correct, int sequence) {
        OptionDto option = new OptionDto();
        option.setContent(content);
        option.setCorrect(correct);
        option.setSequence(sequence);
        return option;
    }

    private Question codeOrder(Course course, LocalDateTime now, Topic topic) {
        CodeOrderQuestionDto details = new CodeOrderQuestionDto();
        details.setLanguage(Languages.Python);
        // The domain shuffles the lists that it gets, so they must be mutable.
        details.setCodeOrderSlots(new ArrayList<>(List.of(slot("def average(values):", 1), slot("    total = sum(values)", 2),
                slot("    return total / len(values)", 3), slot("    print(values)", null))));
        return question(course, now, "Average", "Put the lines in order to compute the average of a list.",
                details, Question.Status.AVAILABLE, topic, null);
    }

    private CodeOrderSlotDto slot(String content, Integer order) {
        CodeOrderSlotDto slot = new CodeOrderSlotDto();
        slot.setContent(content);
        slot.setOrder(order);
        return slot;
    }

    private Question question(Course course, LocalDateTime now, String title, String content, QuestionDetailsDto details,
                              Question.Status status, Topic topic, Topic otherTopic) {
        QuestionDto dto = new QuestionDto();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setStatus(status.name());
        dto.setCreationDate(DateHandler.toISOString(now.minusDays(30)));
        dto.setQuestionDetailsDto(details);
        Question question = new Question(course, dto);
        question.addTopic(topic);
        if (otherTopic != null) question.addTopic(otherTopic);
        return questionRepository.save(question);
    }

    private Quiz quiz(String title, Quiz.QuizType type, CourseExecution execution, LocalDateTime available,
                      LocalDateTime conclusion, LocalDateTime results, List<Question> questions) {
        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setType(type.name());
        quiz.setCreationDate(available.minusDays(1));
        quiz.setAvailableDate(available);
        if (conclusion != null) quiz.setConclusionDate(conclusion);
        if (results != null) quiz.setResultsDate(results);
        quiz.setScramble(false);
        quiz.setCourseExecution(execution);
        for (Question question : questions) {
            new QuizQuestion(quiz, question, quiz.getQuizQuestionsNumber());
        }
        return quizRepository.save(quiz);
    }

    // A completed answer. Each number is the option that the student chose for the question in that position.
    private QuizAnswer answer(Quiz quiz, Student student, LocalDateTime at, int... chosen) {
        QuizAnswer quizAnswer = new QuizAnswer(student, quiz);
        quizAnswer.setCreationDate(at);
        quizAnswer.setAnswerDate(at.plusMinutes(10));
        quizAnswer.setCompleted(true);
        for (QuestionAnswer questionAnswer : quizAnswer.getQuestionAnswers()) {
            MultipleChoiceQuestion question = (MultipleChoiceQuestion) questionAnswer.getQuizQuestion().getQuestion().getQuestionDetails();
            Option option = question.getOptions().get(chosen[questionAnswer.getSequence()]);
            questionAnswer.setTimeTaken(60_000);
            questionAnswer.setAnswerDetails(new MultipleChoiceAnswer(questionAnswer, option));
        }
        return quizAnswerRepository.save(quizAnswer);
    }

    private Tournament tournament(Student creator, CourseExecution execution, Set<Topic> topics, LocalDateTime start,
                                  LocalDateTime end, int numberOfQuestions) {
        Tournament tournament = new Tournament();
        tournament.setEndTime(end);
        tournament.setStartTime(start);
        tournament.setNumberOfQuestions(numberOfQuestions);
        tournament.setCanceled(false);
        tournament.setCreator(creator);
        tournament.setCourseExecution(execution);
        tournament.setTopics(topics);
        tournament.setPassword("");
        tournament.setPrivateTournament(false);
        return tournamentRepository.save(tournament);
    }

    private void submission(Course course, CourseExecution execution, Student submitter, LocalDateTime at, String title,
                            String content, Topic topic, String correct, String... wrong) {
        String[] options = new String[wrong.length + 1];
        options[0] = correct;
        System.arraycopy(wrong, 0, options, 1, wrong.length);
        MultipleChoiceQuestionDto details = new MultipleChoiceQuestionDto();
        List<OptionDto> optionDtos = new ArrayList<>();
        for (int index = 0; index < options.length; index++) {
            OptionDto option = new OptionDto();
            option.setContent(options[index]);
            option.setCorrect(index == 0);
            optionDtos.add(option);
        }
        details.setOptions(optionDtos);
        QuestionDto dto = new QuestionDto();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setStatus(Question.Status.SUBMITTED.name());
        dto.setCreationDate(DateHandler.toISOString(at));
        dto.setQuestionDetailsDto(details);
        Question question = new Question(course, dto);
        question.setOrigin(Question.Origin.STUDENT_SUBMITTED);
        question.addTopic(topic);
        questionRepository.save(question);
        questionSubmissionRepository.save(new QuestionSubmission(execution, question, submitter));
    }
}
